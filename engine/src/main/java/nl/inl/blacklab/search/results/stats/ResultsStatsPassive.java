package nl.inl.blacklab.search.results.stats;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/** ResultsStats that relies on being informed of progress by its owner. */
public class ResultsStatsPassive extends ResultsStats {

    public ResultsStatsPassive() {
        this(ResultsAwaiter.THROWING, Long.MAX_VALUE, Long.MAX_VALUE);
    }

    public ResultsStatsPassive(long maxHitsToProcess, long maxHitsToCount) {
        this(ResultsAwaiter.THROWING, maxHitsToProcess, maxHitsToCount);
    }

    public ResultsStatsPassive(ResultsAwaiter resultsAwaiter) {
        this(resultsAwaiter, Long.MAX_VALUE, Long.MAX_VALUE);
    }

    public ResultsStatsPassive(ResultsAwaiter resultsAwaiter, long maxHitsToProcess, long maxHitsToCount) {
        super(resultsAwaiter);
        this.maxHitsToProcess = maxHitsToProcess;
        this.maxHitsToCount = maxHitsToCount;
    }

    private final LongAdder processed = new LongAdder();

    private final LongAdder counted = new LongAdder();

    private final AtomicBoolean done = new AtomicBoolean(false);

    private MaxStats maxStats = MaxStats.NOT_EXCEEDED;

    public long getMaxHitsToProcess() {
        return maxHitsToProcess;
    }

    public long getMaxHitsToCount() {
        return maxHitsToCount;
    }

    private final long maxHitsToProcess;

    private final long maxHitsToCount;

    public long processedSoFar() {
        return processed.sum();
    }

    public long countedSoFar() {
        return counted.sum();
    }

    public boolean done() {
        return done.get();
    }

    public synchronized MaxStats maxStats() {
        return maxStats;
    }

    @Override
    public String toString() {
        return "ResultsStatsPassive [processed=" + processedSoFar() + ", counted=" + countedSoFar() + ", maxStats=" + maxStats + ", done=" + done + "]";
    }

    public void setDone() {
        this.done.set(true);
    }

    public void increment(boolean storeThisHit) {
        add(1, storeThisHit ? 1 : 0);
    }

    public synchronized void add(long processed, long counted) {
        this.processed.add(processed);
        if (this.processed.sum() >= maxHitsToProcess)
            maxStats = maxStats.maxToProcessReached();
        this.counted.add(counted);
        if (this.counted.sum() >= maxHitsToCount)
            maxStats = maxStats.maxToCountReached();
    }
}
