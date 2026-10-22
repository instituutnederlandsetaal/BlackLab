package nl.inl.blacklab.searches;

import java.util.concurrent.Future;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.QueryTimings;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * A 'recipe' of search operations.
 *
 * Subclasses should be immutable.
 *
 * @param <R> results type, e.g. Hits
 */
public interface Search<R extends SearchResult> {

    /**
     * Execute the search operation, returning the final response.
     *
     * Executes the search synchronously.
     *
     * This search may be queued (i.e. kept from starting until later) if load is high. To avoid this (and potential
     * deadlock situations because of it), use {@link #executeNoQueue()} or {@link #execute(boolean)}.
     *
     * @return result of the operation
     */
    default R execute() throws InvalidQuery {
        return execute(true);
    }

    /**
     * Execute the search operation, returning the final response.
     *
     * Executes the search synchronously.
     *
     * This search will be started right away, even if load is high.
     * For more control, use {@link #execute(boolean)}.
     *
     * @return result of the operation
     */
    default R executeNoQueue() throws InvalidQuery {
        return execute(false);
    }

    /**
     * Execute the search operation, returning the final response.
     *
     * Executes the search synchronously.
     *
     * @param allowQueue may this search be queued (i.e. kept from starting until later)
     * if load is high? Set to false (for searches initiated by other searches)
     * to prevent deadlock,
     * or true (for user-initiated searches) to allow load management to happen.
     * @return result of the operation
     */
    R execute(boolean allowQueue) throws InvalidQuery;

    /**
     * Execute the search operation asynchronously.
     *
     * Runs the search in a separate thread and passes the result through
     * the returned Future.
     *
     * This search may be queued (i.e. kept from starting until later) if load is high.
     * To avoid this (and potential deadlock situations because of it), use
     * {@link #executeAsyncNoQueue()} or {@link #executeAsync(boolean)}.
     *
     * @return future result
     */
    default SearchCacheEntry<R> executeAsync() {
        return executeAsync(true);
    }

    /**
     * Execute the search operation asynchronously.
     *
     * Runs the search in a separate thread and passes the result through
     * the returned Future.
     *
     * This search will be started right away, even if load is high.
     * For more control, use {@link #executeAsync(boolean)}.
     *
     * @return future result
     */
    default SearchCacheEntry<R> executeAsyncNoQueue() {
        return executeAsync(false);
    }

    /**
     * Execute the search operation asynchronously.
     *
     * Runs the search in a separate thread and passes the result through
     * the returned Future.
     *
     * @param allowQueue may this search be queued (i.e. kept from starting until later)
     * if load is high? Set to false (for searches initiated by other searches) to
     * prevent deadlock,
     * or true (for user-initiated searches) to allow load management to happen.
     *
     * @return future result
     */
    SearchCacheEntry<R> executeAsync(boolean allowQueue);

    /**
     * Actually executes this search operation.
     *
     * This is where subclasses should implement the actual search operation.
     *
     * executeAsync submits this search to the cache, which then calls this method from a Runnable
     * to run the search.
     *
     * @param activeSearch where some operations will report on their progress, for example
     *                 to be able to monitor the running count while hits are being fetched
     * @return result of the operation
     */
    R executeInternal(ActiveSearch<R> activeSearch) throws InvalidQuery;

    /**
     * Return the peek object, given a cache entry.
     *
     * This object will be returned when SearchCacheEntry.peek() is called while
     * the search is executing. It might return a ResultsStats object that will return 0
     * while there's no real count available yet, but will return the real count once
     * it's available.
     *
     * Right now this exists purely to monitor counts while searching.
     *
     * @param future future result object
     * @return peek object, or null if not supported for this operation
     */
    default R peekObject(Future<R> future) { return null; }

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    QueryInfo queryInfo();

    QueryTimings timings();

    @Override
    String toString();
}
