package nl.inl.blacklab.searches;

import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * Abstract base class for all Search implementations,
 * to enforce that equals() and hashCode are implemented
 * (to ensure proper caching)
 *
 * @param <R> results type, e.g. Hits
 */
public abstract class AbstractSearch<R extends SearchResult> implements Search<R> {

    private final QueryInfo queryInfo;

    public AbstractSearch(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
    }

    @Override
    public SearchCacheEntry<R> executeAsync(boolean allowQueue) {
        return queryInfo.index().cache().getAsync(this, allowQueue);
    }

    /**
     * Wait for the result from a search task.
     *
     * Will throw InvalidQuery if the query couldn't executed, or an AssertionError in
     * case of other errors.
     *
     * @param future search task
     * @return result
     * @param <R> result type
     */
    private static <R extends SearchResult> R getResult(SearchCacheEntry<R> future) throws InvalidQuery {
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CompletionException) {
                try {
                    throw e.getCause().getCause();
                } catch (InvalidQuery e2) {
                    throw e2;
                } catch (Throwable e2) {
                    throw new AssertionError(e2);
                }
            }
            throw BlackLabRuntimeException.wrap(e.getCause() == null ? e : e.getCause());
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        } catch (CompletionException e) {
            try {
                throw e.getCause();
            } catch (InvalidQuery e2) {
                throw e2;
            } catch (Throwable e2) {
                throw new AssertionError(e2);
            }
        }
    }

    @Override
    public final R execute(boolean allowQueue) throws InvalidQuery {
        SearchCacheEntry<R> future = executeAsync(allowQueue);
        return getResult(future);
    }

    /**
     * Actually execute this search operation.
     *
     * @param activeSearch represents the search being executed (also the "cache entry"), allows us to
     *                   report progress (running count) and how long the task (originally) took
     * @return results of the search operation
     */
    @Override
    public abstract R executeInternal(ActiveSearch<R> activeSearch) throws InvalidQuery;

    /**
     * Execute a child search whose results we need.
     *
     * Will block until the results are available. Will not queue the child search.
     *
     * Will pause our timer, add the processing time from the child search, and
     * resume our timer.
     *
     * @param task parent task that needs the child search's result
     * @param childSearch child search to execute
     * @return results from the child search
     * @param <R> result type
     */
    protected static <R extends SearchResult> R executeChildSearch(ActiveSearch<?> task, Search<R> childSearch) throws InvalidQuery {
        // Don't time subtask now, because it could be in the cache.
        // Instead, pause our timer and ask the subtask to report its original processing time (see below).
        if (task != null)
            task.timer().stop();
        try {

            // Get the subtask results and add its original processing time to our own
            SearchCacheEntry<R> childSearchEntry = childSearch.executeAsync(false);
            R result = getResult(childSearchEntry);
            if (task != null)
                task.timer().add(childSearchEntry.timer().time());
            return result;

        } finally {
            // Resume our own timer
            if (task != null)
                task.timer().start();
        }
    }

    @Override
    public QueryInfo queryInfo() {
        return queryInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AbstractSearch<?> that))
            return false;
        return Objects.equals(queryInfo, that.queryInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(queryInfo);
    }

    protected static String toString(String operation, Object... param) {
        return operation + "(" + StringUtils.join(param, ", ") + ")";
    }

}
