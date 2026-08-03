package nl.inl.blacklab.server.search;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.searches.SearchCacheEntryFromFuture;
import nl.inl.blacklab.server.config.BLSConfig;

/**
 * Alternative, simpler cache implementation that isolates cached searches per request id.
 *
 * Uses caffeine. Caches according to a simple LRU algorithm as opposed to the complex scoring
 * system the default cache uses.
 * May be preferable to the default cache for many requests on relatively small indexes.
 */
public class SimpleFastPerRequestCache implements SearchCache {
    private static final Logger logger = LogManager.getLogger(SimpleFastPerRequestCache.class);
    private final ExecutorService threadPool;
    private final AsyncLoadingCache<Search<? extends SearchResult>, SearchResult> searchCache;
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

    public SimpleFastPerRequestCache(BLSConfig config, ExecutorService threadPool)  {
        this.threadPool = threadPool;

        CacheLoader<Search<? extends SearchResult>, SearchResult> cacheLoader = search -> {
            Future<CacheEntryWithResults<? extends SearchResult>> job = runningJobs.computeIfAbsent(search, (search_) -> SimpleFastPerRequestCache.this.threadPool.submit(() -> {
                final long startTime = System.currentTimeMillis();
                SearchResult results = search.executeInternal(null);
                return new CacheEntryWithResults<>(results, System.currentTimeMillis() - startTime);
            }));
            try {
                CacheEntryWithResults<? extends SearchResult> searchResult = job.get();
                logger.warn("Internal search time is: {}", searchResult.timeUserWaitedMs());
                return searchResult.getResults();
            } finally {
                runningJobs.remove(search);
            }
        };

        int maxSize = config.getCache().getMaxNumberOfJobs();
        logger.info("Creating cache with maxSize:{}", maxSize);
        searchCache = Caffeine.newBuilder()
            .recordStats()
            .maximumSize(maxSize)
            .initialCapacity(maxSize / 10)
            .buildAsync(cacheLoader);
    }

    @Override
    public <T extends SearchResult> SearchCacheEntry<T> getAsync(final Search<T> search, final boolean allowQueue) {
        try {
            CompletableFuture<SearchResult> resultsFuture = searchCache.get(search);
            return new SearchCacheEntryFromFuture(resultsFuture, search);
        } catch (Exception ex) {
            throw BlackLabException.wrapRuntime(ex);
        }
    }

    @Override
    public <R extends SearchResult> boolean containsKey(Search<R> search) {
        return searchCache.asMap().containsKey(search);
    }

    @Override
    public <T extends SearchResult> SearchCacheEntry<T> remove(Search<T> search) {
        SearchResult searchResult = searchCache.synchronous().asMap().remove(search);
        if (searchResult != null) {
            return new CacheEntryWithResults(searchResult, -1);
        }
        return null;
    }

    @Override
    public void removeSearchesForIndex(BlackLabIndex index) {
        logger.info("Removing searches for index: {}", index.name());
        searchCache.asMap().keySet().removeIf(s -> s.queryInfo().index() == index);
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
