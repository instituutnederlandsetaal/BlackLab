package nl.inl.blacklab.search.results;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

/** ResultsStats that relies on being informed of progress by its owner. */
public class ResultsStatsPassive extends ResultsStats {

    public ResultsStatsPassive() {
        this(new ThrowingResultsAwaiter());
    }

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
        public boolean hitsProcessedExceededMaximum() {
            return processed.get() >= maxHitsToProcess;
        }

        @Override
        public boolean hitsCountedExceededMaximum() {
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
        return done.get() || (maxStats.hitsProcessedExceededMaximum() && maxStats.hitsCountedExceededMaximum());
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

    public long getAndUpdateCount(LongUnaryOperator incrementCountUnlessAtMax) {
        return this.counted.getAndUpdate(incrementCountUnlessAtMax);
    }

    public long getAndUpdateProcessed(LongUnaryOperator incrementProcessUnlessAtMax) {
        return this.processed.getAndUpdate(incrementProcessUnlessAtMax);
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
