package nl.inl.blacklab.search.results;

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

    /** Used to return from cache entry if search hasn't been started yet. */
    public static final ResultsStats SEARCH_NOT_STARTED_YET = new ResultsStats() {

        @Override
        public long processedSoFar() {
            return 0;
        }

        @Override
        public long countedSoFar() {
            return 0;
        }

        @Override
        public boolean done() {
            return false;
        }

        @Override
        public MaxStats maxStats() {
            return MaxStats.NOT_EXCEEDED;
        }

        @Override
        public String toString() {
            return "ResultsStats.SEARCH_NOT_STARTED_YET";
        }
    };

    protected ResultsStats() {
        this(ThrowingResultsAwaiter.INSTANCE);
    }

    protected ResultsStats(ResultsAwaiter waitUntil) {
        this.waitUntil = waitUntil;
    }

    protected void setResultsAwaiter(ResultsAwaiter waitUntil) {
        this.waitUntil = waitUntil;
    }

    private ResultsAwaiter waitUntil;

    /**
     * Get the progress awaiter object, that we can ask to wait until e.g. all hits have been processed.
     *
     * @return an object that can be used to wait for certain conditions
     */
    public ResultsAwaiter waitUntil() {
        return waitUntil;
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
     * Is this a static count?
     *
     * @return true if this is a static (saved) count, false if it is dynamically linked to a search
     */
    public boolean isSavedCount() {
        return false;
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
