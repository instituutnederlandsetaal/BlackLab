package nl.inl.blacklab.searches;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.concurrent.ConcurrentUtils;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;

/** A cache that permanently stores anything added to it.
 *  Intended to be discarded after handling a request! */
public final class SearchCacheMap implements SearchCache {

    Map<Search<?>, SearchCacheEntry<? extends SearchResult>> cache = new HashMap<>();

    @Override
    public <R extends SearchResult> SearchCacheEntry<R> getAsync(Search<R> search, boolean allowQueue) {
        SearchCacheEntry<R> future;
        future = (SearchCacheEntry<R>) cache.get(search);
        if (future == null) {
            // Create, add and start the cache entry.
            future = SearchCacheEntry.fromFuture(ConcurrentUtils.constantFuture(search.executeInternal(null)), search);
            cache.put(search, future);
        }
        return future;
    }

    @Override
    public <R extends SearchResult> boolean containsKey(Search<R> source) {
        return cache.containsKey(source);
    }

    @Override
    public <R extends SearchResult> SearchCacheEntry<R> remove(Search<R> search) {
        return (SearchCacheEntry<R>) cache.remove(search);
    }

    @Override
    public void removeSearchesForIndex(BlackLabIndex index) {
        // not implemented, this cache will be dumped soon anyway
    }

    @Override
    public void clear(boolean terminateRunning) {
        for (SearchCacheEntry<?> result : cache.values()) {
            result.cancel(true);
        }
        cache.clear();
    }

    @Override
    public void cleanup() {
        clear(true);
    }
}
