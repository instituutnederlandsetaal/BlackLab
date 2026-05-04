package nl.inl.blacklab.searches;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * A cache that BlackLab will notify of search results, and will query for
 * previously cached results.
 *
 * It is up to the application to implement the cache, deciding what to cache
 * and ensuring the cache doesn't grow too large.
 *
 * The cache implementation should be made thread-safe.
 */
public interface SearchCache {

    /**
     * Get a [future] result for the specified search.
     *
     * If the search is already in the cache, its existing future will be returned.
     * It may or may not have completed already. If the search is not in the cache, it
     * will be started on a new thread and added to the cache, then its future will be
     * returned.
     *
     * @param <R> type of SearchResult
     * @param search search we want the result for. if the search is not in the cache, execute it,
     *            returning the future result and putting it in the cache.
     * @param allowQueue allow this search to be queued (i.e. kept from starting until later)
     * if load is too high right now?
     * @return the future, either one that was already the cache or a new one
     */
    <R extends SearchResult> SearchCacheEntry<R> getAsync(Search<R> search, boolean allowQueue);

    /**
     * Is the specified search in the cache?
     *
     * @param source search to check for
     * @return true if in the cache, false if not
     * @param <R> type of SearchResult
     */
    <R extends SearchResult> boolean containsKey(Search<R> source);

    /**
     * Remove a search from the cache.
     *
     * @param <R> type of SearchResult
     * @param search search to remove
     * @return the removed search's future
     */
    <R extends SearchResult> SearchCacheEntry<R> remove(Search<R> search);

    /**
     * Remove all searches for a particular index.
     *
     * @param index index to remove searches for
     */
    void removeSearchesForIndex(BlackLabIndex index);

    /**
     * Clear the cache.
     *
     * @param cancelRunning if false: only clear searches that are not running. If true: cancel any running searches and clear them as well.
     */
    void clear(boolean cancelRunning);

    /**
     * Perform any final cleanup.
     */
    void cleanup();

    /**
     * Get the cache status.
     * @return a map containing various properties relevant to the cache
     */
    default Map<String, Object> getStatus() {
        return null;
    }

    /**
     * Get the cache contents.
     * @return list of maps with information about cache entries
     */
    default List<Map<String, Object>> getContents(boolean includeDebugInfo) {
        return null;
    }
}
