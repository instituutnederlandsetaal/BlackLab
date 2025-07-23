package nl.inl.blacklab.search.results;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import nl.inl.blacklab.exceptions.InterruptedSearch;

/**
 * Returns 0 until it gets the real stats.
 *
 * Useful for peeking at count.
 */
public class ResultsStatsDelegate extends ResultsStats {

    /** How often should we poll for the real stats to be available in realStats()? */
    private static final long STATS_POLL_TIME_MS = 50;

    /** Our cache entry */
    private final Future<ResultsStats> future;

    /** The actual stats to monitor, as soon as they're available. Null otherwise. */
    private ResultsStats realStats;

    /** Used to let us wait until the stats are available. */
    private final CountDownLatch realStatsAvailable;

    public void setRealStats(ResultsStats realStats) {
        this.realStats = realStats;
        realStatsAvailable.countDown();
    }

    public ResultsStatsDelegate(Future<ResultsStats> future) {
        this.future = future;
        realStatsAvailable = new CountDownLatch(1);
    }

    /**
     * Get the running count, or a fake 0 count if not started yet.
     */
    private ResultsStats stats() {
        if (future.isCancelled())
            throw InterruptedSearch.cancelled();
        try {
            if (future.isDone())
                return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            throw new InterruptedSearch(e);
        } catch (ExecutionException e) {
            throw new InterruptedSearch(e);
        }
        // Either return 0, or the running count object if we have it available
        return realStats == null ? ResultsStats.SEARCH_NOT_STARTED_YET : realStats;
    }

    /**
     * Get the running count, even if we have to wait for a while to get it.
     */
    private ResultsStats realStats() {

        try {

            // Wait until real running count is available or the search
            // has been cancelled.
            while (true) {

                // Check that the search hasn't been cancelled
                if (future.isCancelled())
                    throw InterruptedSearch.cancelled();

                // If search is completely done, return the final stats.
                if (future.isDone())
                    return future.get();

                // Search is not done, and we need the running stats.
                // Wait a short time for them.
                if (realStatsAvailable.await(STATS_POLL_TIME_MS, TimeUnit.MILLISECONDS)) {
                    return realStats;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            throw new InterruptedSearch(e);
        } catch (ExecutionException e) {
            throw new InterruptedSearch(e);
        }
    }

    @Override
    public ResultsAwaiter waitUntil() {
        return realStats().waitUntil();
    }

    @Override
    public long processedSoFar() {
        return stats().processedSoFar();
    }

    @Override
    public long countedSoFar() {
        return stats().countedSoFar();
    }

    @Override
    public boolean done() {
        return stats().done();
    }

    @Override
    public boolean isSavedCount() {
        if (future.isCancelled())
            throw InterruptedSearch.cancelled();
        if (future.isDone())
            return stats().isSavedCount();
        return false;
    }

    @Override
    public MaxStats maxStats() {
        return stats().maxStats();
    }

    @Override
    public String toString() {
        return "ResultsStatsDelegate{cacheEntry=" + future + "}";
    }
}
