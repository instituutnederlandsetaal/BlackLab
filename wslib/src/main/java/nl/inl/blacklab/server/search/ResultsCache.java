package nl.inl.blacklab.server.search;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.searches.SearchCacheEntryFromFuture;
import nl.inl.blacklab.server.config.BLSConfig;

public class ResultsCache implements SearchCache {
    private static final Logger logger = LogManager.getLogger(ResultsCache.class);
    private static final String CACHE_NAME_FOR_METRICS = "blacklab-results-cache";
    private final ExecutorService threadPool;
    private final AsyncLoadingCache<SearchInfoWrapper, SearchResult> searchCache;
    private final ConcurrentHashMap<Search<? extends SearchResult>, Future<CacheEntryWithResults<? extends SearchResult>>> runningJobs = new ConcurrentHashMap<>();


    public static class CacheEntryWithResults<T extends SearchResult> extends SearchCacheEntry<T> {

        private final T results;
        private final long runTime;

        public CacheEntryWithResults(T results, long runTime) {
            this.results = results;
            this.runTime = runTime;
        }

        public T getResults() {
            return results;
        }

        @Override
        public boolean wasStarted() {
            return true;
        }

        @Override
        public void start() {
        }

        @Override
        public long timeUserWaitedMs() {
            return runTime;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() {
            return results;
        }

        @Override
        public T get(long timeout, TimeUnit unit) {
            return results;
        }

        @Override
        public T peek() {
            return results;
        }

    }

    private record SearchInfoWrapper(Search<? extends SearchResult> search, String requestId) {

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SearchInfoWrapper that = (SearchInfoWrapper) o;
            return search.equals(that.search);
        }

        @Override
        public int hashCode() {
            return search.hashCode();
        }
    }

    public ResultsCache(BLSConfig config, ExecutorService threadPool)  {
        this.threadPool = threadPool;

        CacheLoader<SearchInfoWrapper, SearchResult> cacheLoader = searchWrapper -> {
            final String requestId = searchWrapper.requestId();
            ThreadContext.put("requestId", requestId);
            Future<CacheEntryWithResults<? extends SearchResult>> job = runningJobs.computeIfAbsent(searchWrapper.search(), (search) -> ResultsCache.this.threadPool.submit(() -> {
                ThreadContext.put("requestId", requestId);
                final long startTime = System.currentTimeMillis();
                SearchResult results = search.executeInternal(null);
                ThreadContext.remove("requestId");
                return new CacheEntryWithResults<>(results, System.currentTimeMillis() - startTime);
            }));
            try {
                CacheEntryWithResults<? extends SearchResult> searchResult = job.get();
                logger.warn("Internal search time is: {}", searchResult.timeUserWaitedMs());
                return searchResult.getResults();
            } finally {
                runningJobs.remove(searchWrapper.search());
            }
        };

        int maxSize = config.getCache().getMaxNumberOfJobs();
        logger.info("Creating cache with maxSize:{}", maxSize);
        searchCache = Caffeine.newBuilder()
            .recordStats()
            .maximumSize(maxSize)
            .initialCapacity(maxSize / 10)
            .buildAsync(cacheLoader);
        CaffeineCacheMetrics.monitor(Metrics.globalRegistry, searchCache, CACHE_NAME_FOR_METRICS);
        Metrics.globalRegistry.gaugeMapSize("blacklab-job-queue", Tags.empty(), runningJobs);
    }
    @Override
    public <T extends SearchResult> SearchCacheEntry<T> getAsync(final Search<T> search, final boolean allowQueue) {
        try {
            CompletableFuture<SearchResult> resultsFuture = searchCache.get(new SearchInfoWrapper(search, ThreadContext.get("requestId")));
            return new SearchCacheEntryFromFuture(resultsFuture, search);
        } catch (Exception ex) {
            throw BlackLabRuntimeException.wrap(ex);
        }
    }

    @Override
    public <T extends SearchResult> SearchCacheEntry<T> remove(Search<T> search) {
        SearchInfoWrapper searchWrapper = new SearchInfoWrapper(search, null);
        SearchResult searchResult = searchCache.synchronous().asMap().remove(searchWrapper);
        if (searchResult != null) {
            return new CacheEntryWithResults(searchResult, -1);
        }
        return null;
    }

    @Override
    public void removeSearchesForIndex(BlackLabIndex index) {
        logger.info("Removing searches for index: {}", index.name());
        searchCache.asMap().keySet().removeIf(s -> s.search().queryInfo().index() == index);
    }

    @Override
    public void clear(boolean cancelRunning) {
        searchCache.synchronous().invalidateAll();
    }

    @Override
    public void cleanup() {
        clear(true);
    }

}
