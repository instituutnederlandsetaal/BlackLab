package nl.inl.blacklab.server.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BLSConfigPerformance {

    private static final Logger logger = LogManager.getLogger(BLSConfigPerformance.class);

    /** Minimum for maxConcurrentSearches when autodetecting. */
    private static final int CONCURRENT_SEARCHES_AUTO_MIN = 4;

    /** When autodetecting maxThreadsPerSearch, divide #CPUs by this number */
    private static final int THREADS_PER_SEARCH_AUTO_DIVIDER = 4;

    /** Minimum for maxThreadsPerSearch when autodetecting. */
    private static final int THREADS_PER_SEARCH_AUTO_MIN = 2;

    /** Maximum for maxThreadsPerSearch when autodetecting. */
    private static final int THREADS_PER_SEARCH_AUTO_MAX = 6;

    /** How many search jobs may be running at the same time. */
    int maxConcurrentSearches = -1;

    /** How many threads a single search job may use. */
    int maxThreadsPerSearch = -1;

    /** When to abort a count that no client has asked for (seconds). */
    int abandonedCountAbortTimeSec = 30;

    /** How many running jobs one logged-in user may have. */
    int maxRunningJobsPerUser = 10;

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
        if (maxThreadsPerSearch < 0)
            setDefaultMaxThreadsPerSearch();
        return maxThreadsPerSearch;
    }

    private void setDefaultMaxThreadsPerSearch() {
        int n = Runtime.getRuntime().availableProcessors() / THREADS_PER_SEARCH_AUTO_DIVIDER;
        n = Math.max(Math.min(n, THREADS_PER_SEARCH_AUTO_MAX), THREADS_PER_SEARCH_AUTO_MIN);
        logger.debug("performance.maxThreadsPerSearch not configured, setting it to clamp(CPUS / " +
                THREADS_PER_SEARCH_AUTO_DIVIDER + ", " + THREADS_PER_SEARCH_AUTO_MIN + ", " +
                THREADS_PER_SEARCH_AUTO_MAX + ") == " + n);
        maxThreadsPerSearch = n;
    }

    @SuppressWarnings("unused")
    public void setMaxThreadsPerSearch(int maxThreadsPerSearch) {
        this.maxThreadsPerSearch = maxThreadsPerSearch;
    }

    public int getMaxRunningJobsPerUser() {
        return maxRunningJobsPerUser;
    }

    @SuppressWarnings("unused")
    public void setMaxRunningJobsPerUser(int maxRunningJobsPerUser) {
        this.maxRunningJobsPerUser = maxRunningJobsPerUser;
    }

    public int getAbandonedCountAbortTimeSec() {
        return abandonedCountAbortTimeSec;
    }

    @SuppressWarnings("unused")
    public void setAbandonedCountAbortTimeSec(int abandonedCountAbortTimeSec) {
        this.abandonedCountAbortTimeSec = abandonedCountAbortTimeSec;
    }

}
