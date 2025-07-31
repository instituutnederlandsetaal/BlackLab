package nl.inl.blacklab.search.results.stats;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

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

    private final AtomicLong processed = new AtomicLong(0);

    private final AtomicLong counted = new AtomicLong(0);

    private final AtomicBoolean done = new AtomicBoolean(false);

    private MaxStats maxStats = new MaxStats() {
        @Override
        public boolean isTooManyToProcess() {
            return processed.get() >= maxHitsToProcess;
        }

        @Override
        public boolean isTooManyToCount() {
            return counted.get() >= maxHitsToCount;
        }
    };

    private final long maxHitsToProcess;

    private final long maxHitsToCount;

    public long processedSoFar() {
        return processed.get();
    }

    public long countedSoFar() {
        return counted.get();
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
        this.counted.incrementAndGet();
        if (storeThisHit)
            this.processed.incrementAndGet();
    }

    public long getAndUpdateCount(BiFunction<Long, Boolean, Long> incrementCountUnlessAtMaxAndBoundary, boolean atDocBoundary) {
        return this.counted.getAndUpdate(l -> incrementCountUnlessAtMaxAndBoundary.apply(l, atDocBoundary));
    }

    public long getAndUpdateProcessed(BiFunction<Long, Boolean, Long> incrementProcessUnlessAtMaxAndBoundary, boolean atDocBoundary) {
        return this.processed.getAndUpdate(l -> incrementProcessUnlessAtMaxAndBoundary.apply(l, atDocBoundary));
    }

    public synchronized void set(long processed, long counted, boolean done) {
        this.processed.set(processed);
        this.counted.set(counted);
        this.done.set(done);
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
}
