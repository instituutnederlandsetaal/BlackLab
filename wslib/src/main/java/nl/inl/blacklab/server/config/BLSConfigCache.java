package nl.inl.blacklab.server.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BLSConfigCache {

    private static final Logger logger = LogManager.getLogger(BLSConfigCache.class);

    /** Divide max heap size by how much to autodetect a value for targetFreeMemMegs? */
    private static final int TARGET_FREE_MEM_AUTO_DIVIDER = 10;

    /** Default autodetect value for targetFreeMemMegs if no max heap size set */
    private static final int TARGET_FREE_MEM_STATIC_DEFAULT = 100;

    /** Divide max heap size by how much to autodetect a value for minFreeMemForSearchMegs? */
    public static final int FREE_MEM_SEARCH_AUTO_DIVIDER = 200;

    /** Minimum autodetect value for minFreeMemForSearchMegs. Also default if not heap size set. */
    public static final int FREE_MEM_SEARCH_AUTO_MIN = 50;

    /** Maximum autodetect value for minFreeMemForSearchMegs */
    public static final int FREE_MEM_SEARCH_AUTO_MAX = 500;

    /** Maximum number of cache entries to keep.
     *
     * Please note that memory use per cache entry may vary wildly.
     */
    int maxNumberOfJobs = 100;

    /** When to abort a search (seconds). */
    int maxSearchTimeSec = 300;

    /** When to remove search from cache (seconds). */
    int maxJobAgeSec = 3600;

    /** How much memory to target when removing jobs from the cache (megabytes). */
    int targetFreeMemMegs = -1;

    /** How much memory must be available to start a search (megabytes). */
    int minFreeMemForSearchMegs = -1;

    /** How long should the client cache a result? (seconds). */
    int clientCacheTimeSec = 3600;

    /** Maximum number of searches to queue. After this, new searches will fail. */
    int maxQueuedSearches = 20;

    /** How long to forbid resubmitting an aborted search for (seconds). */
    private int denyAbortedSearchSec = 600;

    /** Cache implementation to use. */
    private String implementation = "";

    public int getMaxNumberOfJobs() {
        return maxNumberOfJobs;
    }

    @SuppressWarnings("unused")
    public void setMaxNumberOfJobs(int maxNumberOfJobs) {
        this.maxNumberOfJobs = maxNumberOfJobs;
    }

    public int getMaxJobAgeSec() {
        return maxJobAgeSec;
    }

    @SuppressWarnings("unused")
    public void setMaxJobAgeSec(int maxJobAgeSec) {
        this.maxJobAgeSec = maxJobAgeSec;
    }

    public int getTargetFreeMemMegs() {
        if (targetFreeMemMegs < 0)
            setDefaultTargetFreeMemMegs();
        return targetFreeMemMegs;
    }

    private void setDefaultTargetFreeMemMegs() {
        long heapMaxSize = Runtime.getRuntime().maxMemory();
        if (heapMaxSize < Long.MAX_VALUE) {
            // Set to 10% of max. heap size
            targetFreeMemMegs = (int)(heapMaxSize / 1_000_000) / TARGET_FREE_MEM_AUTO_DIVIDER;
            logger.debug("cache.targetFreeMemMegs not configured, setting it to " +
                    "MAX_HEAP_MB / " + TARGET_FREE_MEM_AUTO_DIVIDER + " == " + targetFreeMemMegs);
        } else {
            // Unknown max heap size; use static default value.
            targetFreeMemMegs = TARGET_FREE_MEM_STATIC_DEFAULT;
            logger.debug("cache.targetFreeMemMegs not configured and no max heap size, " +
                    "setting it to " + targetFreeMemMegs);
        }
    }

    @SuppressWarnings("unused")
    public void setTargetFreeMemMegs(int targetFreeMemMegs) {
        this.targetFreeMemMegs = targetFreeMemMegs;
    }

    public int getMinFreeMemForSearchMegs() {
        if (minFreeMemForSearchMegs < 0)
            setDefaultMinFreeMemForSearchMegs();
        return minFreeMemForSearchMegs;
    }

    private void setDefaultMinFreeMemForSearchMegs() {
        long heapMaxSize = Runtime.getRuntime().maxMemory();
        if (heapMaxSize < Long.MAX_VALUE) {
            // Set to percentage of max. heap size, clamped to a reasonable range.
            minFreeMemForSearchMegs = (int)(heapMaxSize / 1_000_000) / FREE_MEM_SEARCH_AUTO_DIVIDER;
            minFreeMemForSearchMegs = Math.min(Math.max(minFreeMemForSearchMegs,
                    FREE_MEM_SEARCH_AUTO_MIN), FREE_MEM_SEARCH_AUTO_MAX);
            logger.debug("cache.minFreeMemForSearchMegs not configured, setting it to " +
                    "clamp(MAX_HEAP_MB / " + FREE_MEM_SEARCH_AUTO_DIVIDER + ", " +
                    FREE_MEM_SEARCH_AUTO_MIN + ", " + FREE_MEM_SEARCH_AUTO_MAX + ") == " +
                    minFreeMemForSearchMegs);
        } else {
            // Unknown max heap size; use static default value.
            minFreeMemForSearchMegs = FREE_MEM_SEARCH_AUTO_MIN;
            logger.debug("cache.minFreeMemForSearchMegs not configured and no max heap size, " +
                    "setting it to " + minFreeMemForSearchMegs);
        }
    }

    @SuppressWarnings("unused")
    public void setMinFreeMemForSearchMegs(int minFreeMemForSearchMegs) {
        this.minFreeMemForSearchMegs = minFreeMemForSearchMegs;
    }

    public int getClientCacheTimeSec() {
        return clientCacheTimeSec;
    }

    @SuppressWarnings("unused")
    public void setClientCacheTimeSec(int clientCacheTimeSec) {
        this.clientCacheTimeSec = clientCacheTimeSec;
    }

    public int getMaxSearchTimeSec() {
        return maxSearchTimeSec;
    }

    @SuppressWarnings("unused")
    public void setMaxSearchTimeSec(int maxSearchTimeSec) {
        this.maxSearchTimeSec = maxSearchTimeSec;
    }

    public int getMaxQueuedSearches() {
        return maxQueuedSearches;
    }

    @SuppressWarnings("unused")
    public void setMaxQueuedSearches(int maxQueuedSearches) {
        this.maxQueuedSearches = maxQueuedSearches;
    }

    public int getDenyAbortedSearchSec() {
        return denyAbortedSearchSec;
    }

    @SuppressWarnings("unused")
    public void setDenyAbortedSearchSec(int denyAbortedSearchSec) {
        this.denyAbortedSearchSec = denyAbortedSearchSec;
    }

    public String getImplementation() {
        return implementation;
    }

    @SuppressWarnings("unused")
    public void setImplementation(String implementation) {
        this.implementation = implementation;
    }

}
