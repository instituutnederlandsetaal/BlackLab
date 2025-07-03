package nl.inl.blacklab.server.search;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.ServerOverloaded;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.config.BLSConfigCache;
import nl.inl.blacklab.server.util.BlsUtils;

public class BlsCache implements SearchCache {

    private static final Logger logger = LogManager.getLogger(BlsCache.class);

    /** Bytes in one megabyte */
    private static final long ONE_MB_BYTES = 1_000_000;

    /** Bytes in one gigabyte */
    private static final long ONE_GB_BYTES = ONE_MB_BYTES * 1_000;

    /** Very rough measure of how large result objects are, based on a Hit (3 ints + 12 bytes object overhead) */
    public static final int SIZE_OF_HIT = 24;

    /**
     * Returns the amount of memory that can still be allocated before we get the
     * OutOfMemory exception.
     *
     * @return the amount of memory that can still be allocated
     */
    public static long getFreeMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.freeMemory() + (runtime.maxMemory() - runtime.totalMemory());
    }

    /**
     * A thread that regularly calls cleanUpSearches() to
     * ensure that cache cleanup continues even if no new requests are coming in.
     */
    class CleanupSearchesThread extends Thread implements UncaughtExceptionHandler {

        private static final int CLEAN_UP_CACHE_INTERVAL_MS = 500;

        /** Construct the load manager thread object. */
        public CleanupSearchesThread() {
            super("CleanupSearches");
            setUncaughtExceptionHandler(this);
        }

        /**
         * Run the thread, performing the requested search.
         */
        @Override
        public void run() {
            while (!interrupted()) {
                try {
                    Thread.sleep(CLEAN_UP_CACHE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    logger.info("CleanupSearchesThread interrupted");
                    return;
                }

                updateCache();
            }
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            logger.error("CleanupSearchesThread threw an exception!");
            e.printStackTrace();
        }

    }

    private final BLSConfigCache config;

    private final int maxConcurrentSearches;

    /** Abort an abandoned count after how much time? (s) */
    private final int abandonedCountAbortTimeSec;

    protected final Map<Search<?>, BlsCacheEntry<? extends SearchResult>> searches = new HashMap<>();

    protected final boolean trace;

    private final boolean cacheDisabled;

    private Comparator<BlsCacheEntry<?>> worthinessComparator;

    private long cacheSizeBytes;

    private CleanupSearchesThread cleanupThread;

    private String previousCacheStatsMessage = "";

    public BlsCache(BLSConfig blsConfig, @SuppressWarnings("unused") ExecutorService executorService) {
        this.config = blsConfig.getCache();
        this.maxConcurrentSearches = blsConfig.getPerformance().getMaxConcurrentSearches();
        this.abandonedCountAbortTimeSec = blsConfig.getPerformance().getAbandonedCountAbortTimeSec();
        this.trace = blsConfig.getLog().getTrace().isCache();
        cacheDisabled = config.getMaxJobAgeSec() == 0 || config.getMaxNumberOfJobs() == 0;

        if (!cacheDisabled) {
            worthinessComparator = (o1, o2) -> {
                long result = o2.worthiness() - o1.worthiness();
                return result == 0 ? 0 : (result < 0 ? -1 : 1);
            };

            cleanupThread = new CleanupSearchesThread();
            cleanupThread.setDaemon(true); // don't prevent JVM from exiting.
            cleanupThread.start();
        }
    }

    /**
     * Clean up at the end of our lifetime.
     */
    @Override
    public void cleanup() {
        if (cleanupThread != null) {
            cleanupThread.interrupt();
            cleanupThread = null;
        }
        clear(true);
    }

    /**
     * Remove all cache entries for the specified index.
     *
     * @param index the index
     */
    @Override
    public void removeSearchesForIndex(BlackLabIndex index) {
        // Iterate over the entries and remove the ones in the specified index
        traceInfo("Remove searches for index: " + index.name());
        Iterator<Entry<Search<?>, BlsCacheEntry<? extends SearchResult>>> it = searches.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Search<?>, BlsCacheEntry<? extends SearchResult>> entry = it.next();
            if (entry.getValue().search().queryInfo().index() == index) {
                entry.getValue().cancel(true);
                it.remove();
            }
        }
    }

    /**
     * Get rid of all the cached Searches.
     *
     * @param cancelRunning if true, cancels all running searches as well.
     */
    @Override
    public synchronized void clear(boolean cancelRunning) {
        for (BlsCacheEntry<? extends SearchResult> cachedSearch : searches.values()) {
            cachedSearch.cancel(true);
        }
        searches.clear();
        traceInfo("Cache cleared.");
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized <R extends SearchResult> BlsCacheEntry<R> getAsync(Search<R> search, boolean allowQueue) {
        //if (trace) logger.debug("getFromCache({}, allowQueue={})", search, allowQueue);
        BlsCacheEntry<R> future;
        boolean useCache = search.queryInfo().useCache() && !cacheDisabled;
        future = useCache ? (BlsCacheEntry<R>) searches.get(search) : null;
        if (future == null) {
            logger.info("not found in cache, starting search: " + search);
            int numQueued = numberOfQueuedSearches();
            if (numQueued >= config.getMaxQueuedSearches()) {
                logger.warn("Can't start new search, too many queued searches (numQueued = " + numQueued + ", maxQueuedSearches = " + config.getMaxQueuedSearches() + ")");
                throw new ServerOverloaded("The server is too busy right now (" + numQueued + " searches queued). Please try again later.");
            }

            // Create the cache entry.
            // Note that all entries start "queued" (i.e. the search itself hasn't been started yet).
            // We will see if it can be started below.
            future = new BlsCacheEntry<>(search);
            if (useCache)
                searches.put(search, future);

            // Can we start the search, or should it remain queued for now?
            if (!allowQueue || !useCache) {
                // No queueing allowed (i.e. subtask required by another subtask). Start the search right away.
                // (we also do this if you bypass the cache, because then queueing doesn't work)
                if (!allowQueue)
                    traceInfo("-- STARTING: {} (TOP-LEVEL SEARCH)", search);
                else
                    traceInfo("-- STARTING: {} (NOT USING CACHE)", search);
                future.start();
            } else {
                // Queueing is allowed.
                // The new search hasn't been started yet (therefore it is "queued").
                // Check if it (or an older queued search) can be started now.
                startSearchIfPossible(false);
                if (future.wasStarted()) {
                    traceInfo("-- STARTING: {} (QUEUEING NOT NECESSARY)", search);
                } else {
                    traceInfo("-- QUEUEING: {}", search);
                }
            }
        } else {
            // Already in cache.
            traceInfo("-- FOUND:    {}", search);
            future.updateLastAccess();
            // If another running search needs this search, but it was queued, start it now.
            if (!allowQueue && !future.wasStarted())
                future.start();
        }
        //traceCacheStats("   CACHE AFTER GET", false);
        return future;
    }

    synchronized String getCacheStats() {
        if (trace) {
            Map<String, Integer> counts = getCountsPerStatus();
            return String.format("%d queued, %d running, %d finished, %d cancelled",
                counts.get("queued"),
                counts.get("running"),
                counts.get("finished"),
                counts.get("cancelled")
            );
        }
        return "";
    }

    private Map<String, Integer> getCountsPerStatus() {
        int queued = 0, running = 0, finished = 0, cancelled = 0;
        for (BlsCacheEntry<? extends SearchResult> entry: searches.values()) {
            if (!entry.wasStarted())
                queued++;
            else if (entry.isCancelled())
                cancelled++;
            else if (entry.isDone())
                finished++;
            else
                running++;
        }
        Map<String, Integer> countPerStatus = new HashMap<>();
        countPerStatus.put("queued", queued);
        countPerStatus.put("running", running);
        countPerStatus.put("finished", finished);
        countPerStatus.put("cancelled", cancelled);
        return countPerStatus;
    }

    void traceCacheStats() {
        if (trace) {
            String msg = getCacheStats();
            if (!msg.equals(previousCacheStatsMessage)) {
                long freeGigsTimesTen = getFreeMemory() * 10 / ONE_GB_BYTES;
                double freeGigs = (double)freeGigsTimesTen / 10;
                traceInfo("{}: {}, {}G free heap", "CACHE AFTER UPDATE", msg, freeGigs);
            }
            previousCacheStatsMessage = msg;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    synchronized public <R extends SearchResult> BlsCacheEntry<R> remove(Search<R> search) {
        BlsCacheEntry<R> future = (BlsCacheEntry<R>) searches.remove(search);
        if (future != null)
            traceInfo("-- REMOVED:  {} ({} searches left)", search, searches.size());
        return future;
    }

    /**
     * Estimate number of result objects (e.g. Hits) in cache.
     *
     * This may not be accurate because Hits are sometimes but not always duplicated
     * between tasks (e.g. gather, sort, group). It gives a rough estimate though
     * that we can use to decide when to clean up.
     *
     * @return estimate of number of Hits in cache
     */
    private synchronized long estimateResultObjectsInCache() {
        // Estimate the total cache size
        long resultsObjectsInCache = 0;
        for (BlsCacheEntry<?> search : searches.values()) {
            resultsObjectsInCache += search.numberOfStoredHits();
        }
        return resultsObjectsInCache;
    }

    void traceInfo(String msg, Object... params) {
        if (trace) {
            logger.info(msg, params);
        }
    }

    public synchronized int numberOfRunningSearches() {
        return (int) searches.values().stream().filter(SearchCacheEntry::isRunning).count();
    }

    private synchronized int numberOfQueuedSearches() {
        return (int) searches.values().stream().filter(s -> !s.wasStarted()).count();
    }

    /**
     * If we can start another search, finds the oldest queued search and start it.
     *
     * @param report if true (and trace is on), report the search we started
     */
    synchronized void startSearchIfPossible(boolean report) {
        // Is server load low enough to start a search?
        if (canStartAnotherSearch()) {
            // Find the oldest queued search and start it.
            // Only start one per iteration (give it a little time to start its subtasks)

            // Sort the searches based on descending "worthiness"
            List<BlsCacheEntry<?>> searches = new ArrayList<>(this.searches.values());
            for (BlsCacheEntry<?> s : searches)
                s.calculateWorthiness(); // calculate once before sorting so we don't run into Comparable contract issues because of threading
            searches.sort(worthinessComparator);

            // Find & start oldest queued search
            BlsCacheEntry<?> search1 = searches.stream().filter(s -> !s.wasStarted()).findFirst().orElse(null);
            if (search1 != null) {
                if (report)
                    traceInfo("-- UNQUEUE:  {}", search1);
                search1.start();
            }
        }
    }

    /**
     * See if server load is low enough that we can start (unqueue) another search.
     *
     * @return true if we can start a search, false if it's still too busy
     */
    private boolean canStartAnotherSearch() {
        int runningSearches = numberOfRunningSearches();
        if (runningSearches == 0)
            return true;
        long freeMemory = getFreeMemory();
        int minFreeMemForSearchMegs = config.getMinFreeMemForSearchMegs();
        boolean enoughMemory = freeMemory / ONE_MB_BYTES >= minFreeMemForSearchMegs;
        boolean threadsAvailable = runningSearches < maxConcurrentSearches;
        return enoughMemory && threadsAvailable;
    }

    /**
     * Abort searches if too much memory is in use or the search is taking too long.
     * Remove older finished searches from cache. Start a queued search if load is low enough.
     */
    synchronized void updateCache() {
        long resultsObjectsInCache = estimateResultObjectsInCache();
        cacheSizeBytes = resultsObjectsInCache * SIZE_OF_HIT;

        List<BlsCacheEntry<?>> searches = new ArrayList<>(this.searches.values());

        // Sort the searches based on descending "worthiness"
        for (BlsCacheEntry<?> s : searches)
            s.calculateWorthiness(); // calculate once before sorting so we don't run into Comparable contract issues because of threading
        searches.sort(worthinessComparator);

        //------------------
        // STEP 1: remove least worthy, finished searches from cache

        // Abort searches that are taking too long (longer than maxSearchTimeSec)
        for (int i = searches.size() - 1; i >= 0; i--) {
            BlsCacheEntry<?> search = searches.get(i);
            int maxSearchTimeSec = config.getMaxSearchTimeSec();
            if (search.isRunning() && search.timeUserWaitedMs() > maxSearchTimeSec * 1000L) {
                // Search is taking too long. Cancel it.
                traceInfo("Search is taking too long (time " + (search.timeUserWaitedMs()/1000) + "s > max time " + maxSearchTimeSec + "s)");
                traceInfo("-- ABORT (taking too long): {}", search);
                String maxTime = BlsUtils.describeIntervalSec(maxSearchTimeSec);
                String retryTime = BlsUtils.describeIntervalSec(config.getDenyAbortedSearchSec());
                search.setReason("Running search aborted because it took longer than " + maxTime + ". " +
                        "This is done to ease server load. If you want to try again, please wait " +
                        retryTime + ".");
                search.cancel(true);
                searches.remove(i);
            }
        }

        // Look at completed searches.
        // Get rid of completed searches that haven't been accessed in a while (maxJobAgeSec).
        // If we're low on memory, remove searches so they will be garbage collected (targetFreeMemMegs).
        boolean checkLastAccessTime = config.getMaxJobAgeSec() >= 0;
        long freeMegs = getFreeMemory() / ONE_MB_BYTES;
        long memoryToFreeUpMegs = config.getTargetFreeMemMegs() - freeMegs;
        for (int i = searches.size() - 1; i >= 0; i--) {
            BlsCacheEntry<?> search = searches.get(i);
            if (!search.isDone())
                continue;

            boolean isSearchTooOld;
            if (search.isCancelled()) {
                // Cancelled (aborted) search kept in cache to prevent clients from resubmitting right away.
                isSearchTooOld = checkLastAccessTime && (search.timeSinceFinishedMs() > config.getDenyAbortedSearchSec() * 1000L ||
                        search.timeSinceCreationMs() > config.getMaxJobAgeSec() * 1000L);
            } else {
                // Finished search.
                isSearchTooOld = checkLastAccessTime && search.timeUnusedMs() > config.getMaxJobAgeSec() * 1000L;
            }
            if (isSearchTooOld || memoryToFreeUpMegs > 0) {
                // Search is too old or cache is too big. Keep removing searches until that's no
                // longer the case
                // logger.debug("Remove from cache: " + search);
                String reason;
                if (memoryToFreeUpMegs > 0) {
                    traceInfo("Not enough free mem (free " + freeMegs + "M < min free "
                            + config.getTargetFreeMemMegs() + "M)");
                    reason = "free up memory";
                } else {
                    traceInfo("Searchjob too old (age " + (int)(search.timeUnusedMs()/1000) + "s > max age "
                            + config.getMaxJobAgeSec() + "s)");
                    reason = "search too old";
                }
                traceInfo("-- REMOVE ({}): {}", reason, search);
                remove(search.search());

                memoryToFreeUpMegs -= search.numberOfStoredHits() * SIZE_OF_HIT / ONE_MB_BYTES; // NB very rough guess, but ok
                searches.remove(i);
            }
        }

        //------------------
        // STEP 2: abort any long-running counts that no client has asked about for a while.
        for (int i = 0; i < searches.size(); i++) {
            BlsCacheEntry<?> cacheEntry = searches.get(i);
            if (cacheEntry.isRunning()) {
                // Running search. Run or abort?
                boolean isCount = cacheEntry.search() instanceof SearchCount;

                // Don't abort count before hits search, as this will fail if a search is so heavy that it cannot
                // produce the first page of hits before its count is aborted.
                int abortCountAfterSec = Math.max(abandonedCountAbortTimeSec, config.getMaxJobAgeSec());

                if (isCount && cacheEntry.timeSinceLastAccessMs() > abortCountAfterSec * 1000L) {
                    // Abandoned counts are removed right away, because we do this quite quickly (e.g. 30s)
                    // and don't want to penalize users if they decide to come back to this search.
                    remove(cacheEntry.search());
                    traceInfo("-- ABORT (abandoned count): {}", cacheEntry);
                    String maxTime = BlsUtils.describeIntervalSec(abortCountAfterSec);
                    cacheEntry.setReason("Running count aborted because of runtime > " + maxTime + " with no requests. " +
                            "This is done to ease server load. If you need the results of this count, please try your search again.");
                    cacheEntry.cancel(true);
                    searches.remove(i);
                    i--; // don't skip an element
                }
            }
        }

        // See if we can start a queued search
        startSearchIfPossible(true);

        // Report the cache status (if it changed)
        traceCacheStats();
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Integer> counts = getCountsPerStatus();
        return Map.of(
            "targetFreeMemMegs", config.getTargetFreeMemMegs(),
            "minFreeMemForSearchMegs", config.getMinFreeMemForSearchMegs(),
            "maxQueuedSearches", config.getMaxQueuedSearches(),
            "maxSearchTimeSec", config.getMaxSearchTimeSec(),
            "maxJobAgeSec", config.getMaxJobAgeSec(),
            "maxSearchAgeSec", config.getMaxJobAgeSec(),
            "sizeBytes", cacheSizeBytes,
            "numberOfSearches", searches.size(),
            "freeMemory", getFreeMemory(),
            "countsPerStatus", Map.of(
                "queued", counts.get("queued"),
                "running", counts.get("running"),
                "finished", counts.get("finished"),
                "cancelled", counts.get("cancelled")
            )
        );
    }

    @Override
    public List<Map<String, Object>> getContents(boolean includeDebugInfo) {
        return searches.values().stream().map(e -> e.getInfo(includeDebugInfo)).collect(Collectors.toList());
    }

}
