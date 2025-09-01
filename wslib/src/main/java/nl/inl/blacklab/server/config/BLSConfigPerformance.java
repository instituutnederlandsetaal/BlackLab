package nl.inl.blacklab.server.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.BlackLabEngine;

public class BLSConfigPerformance {

    private static final Logger logger = LogManager.getLogger(BLSConfigPerformance.class);

    /** Minimum for maxConcurrentSearches when autodetecting. */
    private static final int CONCURRENT_SEARCHES_AUTO_MIN = 4;

    /** How many search jobs may be running at the same time. */
    int maxConcurrentSearches = -1;

    /** How many threads a single search job may use. */
    int maxThreadsPerSearch = -1;

    /** When to abort a count that no client has asked for (seconds). */
    int abandonedCountAbortTimeSec = 30;

    public int getMaxConcurrentSearches() {
        if (maxConcurrentSearches < 0)
            setDefaultMaxConcurrentSearches();
        return maxConcurrentSearches;
    }

    private void setDefaultMaxConcurrentSearches() {
        int n = Math.max(Runtime.getRuntime().availableProcessors() - 1, CONCURRENT_SEARCHES_AUTO_MIN);
        logger.debug("performance.maxConcurrentSearches not configured, setting it to max(CPUS - 1, " +
                CONCURRENT_SEARCHES_AUTO_MIN + ") == " + n + " ");
        maxConcurrentSearches = n;
    }

    @SuppressWarnings("unused")
    public void setMaxConcurrentSearches(int maxConcurrentSearches) {
        this.maxConcurrentSearches = maxConcurrentSearches;
    }

    public int getMaxThreadsPerSearch() {
        if (maxThreadsPerSearch < 0) {
            maxThreadsPerSearch = BlackLabEngine.chooseDefaultMaxThreadsPerSearch();
            logger.debug("performance.maxThreadsPerSearch not configured, setting it to " + maxThreadsPerSearch);
        }
        return maxThreadsPerSearch;
    }

    @SuppressWarnings("unused")
    public void setMaxThreadsPerSearch(int maxThreadsPerSearch) {
        this.maxThreadsPerSearch = maxThreadsPerSearch;
    }

    public int getAbandonedCountAbortTimeSec() {
        return abandonedCountAbortTimeSec;
    }

    @SuppressWarnings("unused")
    public void setAbandonedCountAbortTimeSec(int abandonedCountAbortTimeSec) {
        this.abandonedCountAbortTimeSec = abandonedCountAbortTimeSec;
    }

}
