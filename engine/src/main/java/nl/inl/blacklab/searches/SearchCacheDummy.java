package nl.inl.blacklab.searches;

import org.apache.commons.lang3.concurrent.ConcurrentUtils;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;

/** A dummy cache that does nothing. */
public final class SearchCacheDummy implements SearchCache {

    @Override
    public <R extends SearchResult> SearchCacheEntry<R> getAsync(Search<R> search, boolean allowQueue) {
        try {
            return SearchCacheEntry.fromFuture(ConcurrentUtils.constantFuture(search.executeInternal(null)), search); // It's never in cache, and don't add it either
        } catch (InvalidQuery e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @Override
    public <R extends SearchResult> SearchCacheEntry<R> remove(Search<R> search) {
        // Do nothing.
        return null;
    }

    @Override
    public void removeSearchesForIndex(BlackLabIndex index) {
        // OK
    }

    @Override
    public void clear(boolean terminateRunning) {
        // OK
    }

    @Override
    public void cleanup() {
        // NOP
    }
}
