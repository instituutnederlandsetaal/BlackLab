package nl.inl.blacklab.search.results;

public abstract class ResultsStats implements SearchResult {

    /** Used to return from cache entry if search hasn't been started yet. */
    public static final ResultsStats SEARCH_NOT_STARTED_YET = new ResultsStats() {
        @Override
        public boolean processedAtLeast(long lowerBound) {
            throw new IllegalStateException("cannot access results so far, search not started");
        }

        @Override
        public long processedTotal() {
            throw new IllegalStateException("cannot access total, search not started");
        }

        @Override
        public long processedSoFar() {
            return 0;
        }

        @Override
        public long countedSoFar() {
            return 0;
        }

        @Override
        public long countedTotal() {
            throw new IllegalStateException("cannot access total, search not started");
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

    /**
     * Block until this many hits have been processed.
     *
     * Returns false if there's not enough hits to process.
     *
     * @param lowerBound number of hits to wait for
     * @return true if this many hits are now available, false if not
     */
    public abstract boolean processedAtLeast(long lowerBound);

    /**
     * This is an alias of resultsProcessedTotal().
     *
     * @return number of hits processed total
     */
    public long size() {
        return processedTotal();
    }

    public abstract long processedTotal();

    public abstract long processedSoFar();

    public abstract long countedSoFar();

    public abstract long countedTotal();

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
    public ResultsStats save() {
        return new ResultsStatsStatic(processedSoFar(), countedSoFar(), maxStats());
    }

    /**
     * Is this a static count?
     *
     * @return true if this is a static (saved) count, false if it is dynamically linked to a search
     */
    public boolean isStatic() {
        return false;
    }

    /**
     * Get information about exceeding maximums.
     *
     * @return max stats
     */
    public abstract MaxStats maxStats();

    /**
     * Was this count interrupted?
     *
     * This can happen if you implement a system that aborts long-running or memory-hungry searches.
     * If so, the total counts may not reflect reality.
     *
     * @return true if the count was interrupted, false if not
     */
    public boolean wasInterrupted() {
        return false;
    }

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
