package nl.inl.blacklab.server.lib;

/**
 * Keeps track of various timings we want to report to the client.
 */
public class SearchTimings {

    /**
     * How much time each required search task originally took to produce this response.
     */
    private final long processingTime;

    /**
     * How long it took to count all the results.
     */
    private final long countTime;

    public SearchTimings(long processingTime, long countTime) {
        this.processingTime = processingTime;
        this.countTime = countTime;
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public long getCountTime() {
        return countTime;
    }
}
