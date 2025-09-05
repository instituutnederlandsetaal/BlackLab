package nl.inl.blacklab.search.results.stats;

/**
 * Static implementation of ResultsStats, suitable for
 * when a search has ended and we want to ensure we don't
 * keep a reference to the search.
 */
public class ResultsStatsSaved extends ResultsStats {

    /** Used to avoid NPE when ResultsStats is not available for whatever reason. */
    public static final ResultsStats INVALID = new ResultsStatsSaved(-1, -1, MaxStats.get(true, true));

    private final long processed;

    private final long counted;

    private final MaxStats maxStats;

    public ResultsStatsSaved(long processedAndCounted) {
        this(processedAndCounted, processedAndCounted, MaxStats.NOT_EXCEEDED);
    }

    public ResultsStatsSaved(long processed, long counted) {
        this(processed, counted, MaxStats.NOT_EXCEEDED);
    }

    public ResultsStatsSaved(long processed, long counted, MaxStats maxStats) {
        super(new ResultsAwaiter() {
            @Override
            public boolean processedAtLeast(long lowerBound) {
                return processed >= lowerBound;
            }

            @Override
            public long allProcessed() {
                return processed;
            }

            @Override
            public long allCounted() {
                return counted;
            }
        });
        this.processed = processed;
        this.counted = counted;
        this.maxStats = maxStats;
    }

    @Override
    public long processedSoFar() {
        return processed;
    }

    @Override
    public long countedSoFar() {
        return counted;
    }

    @Override
    public boolean done() {
        return true;
    }

    @Override
    public MaxStats maxStats() {
        return maxStats;
    }

    @Override
    public String toString() {
        return "ResultsStatsStatic [processed=" + processed + ", counted=" + counted + ", maxStats=" + maxStats + "]";
    }

    @Override
    public ResultsStatsSaved save() {
        return this;
    }
}
