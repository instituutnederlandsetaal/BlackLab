package nl.inl.blacklab.search.results.stats;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import nl.inl.blacklab.search.results.hits.SpansReader;

/** ResultsStats that relies on being informed of progress by its owner. */
public class ResultsStatsPassive extends ResultsStats {

    public ResultsStatsPassive(ResultsAwaiter waitUntil) {
        this(waitUntil, Long.MAX_VALUE, Long.MAX_VALUE);
    }

    public ResultsStatsPassive(ResultsAwaiter waitUntil, long maxHitsToProcess, long maxHitsToCount) {
        super(waitUntil);
        this.maxHitsToProcess = maxHitsToProcess;
        this.maxHitsToCount = maxHitsToCount;
    }

    private final LongAdder processed = new LongAdder();

    private final LongAdder counted = new LongAdder();

    private final AtomicBoolean done = new AtomicBoolean(false);

    private MaxStats maxStats = new MaxStats() {
        @Override
        public boolean isTooManyToProcess() {
            return processed.sum() >= maxHitsToProcess;
        }

        @Override
        public boolean isTooManyToCount() {
            return counted.sum() >= maxHitsToCount;
        }
    };

    private final long maxHitsToProcess;

    private final long maxHitsToCount;

    public long processedSoFar() {
        return processed.sum();
    }

    public long countedSoFar() {
        return counted.sum();
    }

    public synchronized boolean done() {
        return done.get() || (maxStats.isTooManyToProcess() && maxStats.isTooManyToCount());
    }

    public synchronized MaxStats maxStats() {
        return maxStats;
    }

    @Override
    public String toString() {
        return "ResultsStatsPassive [processed=" + processedSoFar() + ", counted=" + countedSoFar() + ", maxStats=" + maxStats + ", done=" + done + "]";
    }

    public synchronized void setDone() {
        this.done.set(true);
    }

    public void increment(boolean storeThisHit) {
        this.counted.add(1);
        if (storeThisHit)
            this.processed.add(1);
    }

    public void setDone(boolean b) {
        this.done.set(b);
    }

    public synchronized void setDone(MaxStats maxStats) {
        if (maxStats == null)
            throw new IllegalArgumentException("maxStats cannot be null");
        this.maxStats = maxStats;
        setDone(true);
    }

    public synchronized SpansReader.Phase add(long processed, long counted) {
        this.processed.add(processed);
        this.counted.add(counted);
        if (this.counted.sum() >= maxHitsToCount)
            return SpansReader.Phase.DONE;
        else if (this.processed.sum() >= maxHitsToProcess)
            return SpansReader.Phase.COUNTING_ONLY;
        else
            return SpansReader.Phase.STORING_AND_COUNTING;
    }
}
