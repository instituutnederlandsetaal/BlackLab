package nl.inl.blacklab.searches;

import java.util.concurrent.Future;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.util.SearchTimer;

/**
 * An entry in BlackLab's search cache.
 *
 * Custom implementations of SearchCache can subclass this to implement custom logic.
 *
 * @param <R> the type of search result this search will yield
 */
public abstract class SearchCacheEntry<R extends SearchResult> implements Future<R>, ActiveSearch<R> {

    /** Keep track of how long this task and subtasks took to (originally) execute. */
    private final SearchTimer searchTimer = new SearchTimer();

    /**
     * Get the timer keeping track of how long this search (originally) executed.
     * @return the search's task timer
     */
    @Override
    public SearchTimer timer() {
        return searchTimer;
    }

    /**
     * Has this search been started?
     *
     * @return true if it was, false if not
     */
    public abstract boolean wasStarted();

    /**
     * Subclasses should implement this to start the search.
     *
     * Search entries initially haven't been started. We call unstarted searches 'queued',
     * because they are waiting for enough resources to be available to be started.
     * When there are, this method is called to start the search.
     */
    public abstract void start();

    static <S extends SearchResult> SearchCacheEntry<S> fromFuture(Future<S> future, Search<S> search) {
        return new SearchCacheEntryFromFuture<>(future, search);
    }

    /**
     * Is this search currently running?
     * @return true if the search is running (not queued, cancelled or completed)
     */
    public boolean isRunning() {
        return wasStarted() && !isDone();
    }

    /**
     * If this search couldn't complete or was aborted, what it the reason?
     *
     * @return descriptive reason or empty string
     */
    public String getReason() {
        return "";
    }

    /**
     * How long since this search was created?
     *
     * This is used for cache management (e.g. aborting a search that's been
     * running too long), not for reporting processing time (because this value
     * is directly influences by what's in the cache).
     *
     * @return how long the user has waited for this result (ms)
     */
    public abstract long timeUserWaitedMs();

    /**
     * Did this task throw an exception?
     * @return whether an exception was thrown
     */
    public boolean threwException() {
        return false;
    }

    /**
     * Peek at the result even if it's not yet finished.
     *
     * Used for running counts.
     *
     * @return the result so far, or null if not supported for this operation
     */
    @Override
    public R peek() {
        if (isCancelled())
            throw InterruptedSearch.cancelled();
        return null;
    }

}
