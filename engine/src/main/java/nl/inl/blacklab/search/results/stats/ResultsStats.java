package nl.inl.blacklab.search.results.stats;

import nl.inl.blacklab.search.results.SearchResult;

/** A search result that represents the number of results processed and counted.
 * <p>
 * Processed means the results have been retrieved and are included
 * in the result set. Once we hit the maximum number of results to porocess,
 * we stop processing more results, but we continue counting them until we reach
 * the maximum number of results to count.
 */
public abstract class ResultsStats implements SearchResult {

    /** Object to help us wait for various things, such as all results having been processed. */
    public interface ResultsAwaiter {
        boolean processedAtLeast(long lowerBound);
        long allProcessed();
        long allCounted();
    }

    public static class ThrowingResultsAwaiter implements ResultsAwaiter {
        public static final ThrowingResultsAwaiter INSTANCE = new ThrowingResultsAwaiter();

        @Override
        public boolean processedAtLeast(long lowerBound) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long allProcessed() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long allCounted() {
            throw new UnsupportedOperationException();
        }
    }

    protected ResultsStats() {
        this(ThrowingResultsAwaiter.INSTANCE);
    }

    protected ResultsStats(ResultsAwaiter resultsAwaiter) {
        this.resultsAwaiter = resultsAwaiter;
    }

    protected void setResultsAwaiter(ResultsAwaiter resultsAwaiter) {
        this.resultsAwaiter = resultsAwaiter;
    }

    private ResultsAwaiter resultsAwaiter;

    public boolean processedAtLeast(long lowerBound) {
        return resultsAwaiter.processedAtLeast(lowerBound);
    }

    public long processedTotal() {
        return resultsAwaiter.allProcessed();
    }

    public long countedTotal() {
        return resultsAwaiter.allCounted();
    }

    public abstract long processedSoFar();

    public abstract long countedSoFar();

    public abstract boolean done();

    /**
     * Save the current counts to a static object.
     *
     * The resulting object doesn't hold a reference to the search anymore.
     *
     * It only saves the results processed and counted so far, and considers those
     * the totals.
     *
     * @return static instance of current stats
     */
    public ResultsStatsSaved save() {
        return new ResultsStatsSaved(processedSoFar(), countedSoFar(), maxStats());
    }

    /**
     * Get information about exceeding maximums.
     *
     * @return max stats
     */
    public abstract MaxStats maxStats();

    @Override
    public abstract String toString();

    /**
     * How many result objects are stored here?
     */
    @Override
    public long numberOfResultObjects() {
        return 1;
    }

}
