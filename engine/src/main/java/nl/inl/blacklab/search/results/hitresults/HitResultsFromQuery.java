package nl.inl.blacklab.search.results.hitresults;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.QueryTimings;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hits.HitsFromQuery;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;
import nl.inl.util.CurrentThreadExecutorService;

public class HitResultsFromQuery extends HitResultsAbstract {

    /** If another thread is busy fetching hits and we're monitoring it, how often should we check? */
    protected static final int HIT_POLLING_TIME_MS = 50;

    /** Configured upper limit of requestedHitsToProcess, to which it will always be clamped. */
    protected final long maxHitsToProcess;

    /** Configured upper limit of requestedHitsToCount, to which it will always be clamped. */
    protected final long maxHitsToCount;

    /** Query context, keeping track of e.g. match info defitions */
    protected final HitQueryContext hitQueryContext;

    /** Keeps track of hits encountered. */
    protected final ResultsStatsPassive hitsStats;

    /** Keeps track of docs encountered. */
    protected final ResultsStatsPassive docsStats;

    /** Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1 */
    protected final AtomicLong requestedHitsToProcess = new AtomicLong();

    /** Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1 */
    protected final AtomicLong requestedHitsToCount = new AtomicLong();

    /** Used to make sure that only 1 thread can be fetching hits at a time. */
    protected final Lock ensureHitsReadLock = new ReentrantLock();

    /** If true, we're done. */
    protected boolean allSourceSpansFullyRead = false;

    /** Number of threads to use for fetching hits. */
    protected int numThreads;

    private HitsFromQuery lazyHitsView;

    public static HitResultsFromQuery get(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        return new HitResultsFromQuery(queryInfo, sourceQuery, searchSettings);
    }

    /** Objects getting the actual hits from each index segment and adding them to the global results list. */
    protected final List<SpansReader> spansReaders = new ArrayList<>();

    /***
     * Make equal groups of items, so that each group has approximately the same total size.
     * This is useful for distributing work evenly over multiple threads.
     *
     * @param items the items to group
     * @param sizeGetter a function that returns the size of each item, used to determine how to group them
     * @param numberOfGroups the number of groups to create
     * @return a list of groups, each group is a list of items
     * @param <T> the type of items to group
     */
    public static <T> List<List<T>> makeEqualGroups(List<T> items, Function<T, Long> sizeGetter, int numberOfGroups) {
        items.sort(Comparator.comparing(sizeGetter).reversed());

        // Now divide the segments into groups by repeatedly adding the largest remaining segment to
        // the smallest group.
        List<List<T>> groups =
                new ArrayList<>(numberOfGroups);
        List<Long> hitsInGroup = new ArrayList<>(numberOfGroups);
        for (int i = 0; i < numberOfGroups; i++) {
            groups.add(new ArrayList<>()); // create empty group for each thread}
            hitsInGroup.add(0L);
        }
        for (T segment: items) {
            // Find the group with the least hits so far, and add this segment to that group.
            int minGroupIndex = 0;
            for (int i = 1; i < hitsInGroup.size(); i++) {
                if (hitsInGroup.get(i) < hitsInGroup.get(minGroupIndex)) {
                    minGroupIndex = i;
                }
            }
            groups.get(minGroupIndex).add(segment);
            hitsInGroup.set(minGroupIndex, hitsInGroup.get(minGroupIndex) + sizeGetter.apply(segment));
        }
        return groups;
    }

    /** Call optimize() and rewrite() on the source query, and create a weight for it.
     *
     * @param queryInfo query info for this query
     * @param sourceQuery the source query to optimize and rewrite
     * @param fiMatchFactor override FI match threshold (debug use only, -1 means no override)
     * @return the weight for the optimized/rewritten query
     */
    protected static BLSpanWeight rewriteAndCreateWeight(QueryInfo queryInfo, BLSpanQuery sourceQuery,
            long fiMatchFactor) {
        QueryTimings timings = queryInfo.timings();
        timings.start();

        // Override FI match threshold? (debug use only!)
        try {
            BLSpanQuery optimizedQuery;
            synchronized (ClauseCombinerNfa.class) {
                long oldFiMatchValue = ClauseCombinerNfa.getNfaThreshold();
                if (fiMatchFactor != -1) {
                    logger.debug("setting NFA threshold for this query to " + fiMatchFactor);
                    ClauseCombinerNfa.setNfaThreshold(fiMatchFactor);
                }

                sourceQuery.setQueryInfo(queryInfo);
                boolean traceOptimization = BlackLab.config().getLog().getTrace().isOptimization();
                if (traceOptimization)
                    logger.debug("Query before optimize()/rewrite(): " + sourceQuery);

                optimizedQuery = sourceQuery.optimize(queryInfo.index().reader());
                if (traceOptimization)
                    logger.debug("Query after optimize(): " + optimizedQuery);

                optimizedQuery = optimizedQuery.rewrite(queryInfo.index().reader());
                if (traceOptimization)
                    logger.debug("Query after rewrite(): " + optimizedQuery);

                // Restore previous FI match threshold
                if (fiMatchFactor != -1) {
                    ClauseCombinerNfa.setNfaThreshold(oldFiMatchValue);
                }
            }
            timings.record("rewrite");

            // This call can take a long time
            BLSpanWeight weight = optimizedQuery.createWeight(queryInfo.index().searcher(),
                    ScoreMode.COMPLETE_NO_SCORES, 1.0f);
            timings.record("createWeight");
            return weight;
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    /** Number of hits in the global view. Needed because we don't want to call hitsInternalMutable.size() from
     *  HitsFromQueryKeepSegments, because that class doesn't use it. (REFACTOR THIS!) */
    protected long globalHitsSoFar() {
        return lazyHitsView.globalHitsSoFar();
    }

    protected HitResultsFromQuery(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        // NOTE: we explicitly construct HitsInternal so they're writeable
        super(queryInfo.optOverrideField(sourceQuery),
                HitsMutable.create(queryInfo.optOverrideField(sourceQuery).field(), null,
                        -1, true, true),
                true);
        maxHitsToProcess = searchSettings.maxHitsToProcess();
        maxHitsToCount = searchSettings.maxHitsToCount();
        hitQueryContext = new HitQueryContext(queryInfo.index(), null, queryInfo.field()); // each spans will get a copy
        hitsMutable.setMatchInfoDefs(hitQueryContext.getMatchInfoDefs());
        hitsStats = new ResultsStatsPassive(new ResultsAwaiterHits(this), maxHitsToProcess, maxHitsToCount);
        docsStats = new ResultsStatsPassive(new ResultsAwaiterDocs(this));
        numThreads = Math.max(queryInfo.index().blackLab().maxThreadsPerSearch(), 1);

        BLSpanWeight weight = rewriteAndCreateWeight(queryInfo, sourceQuery, searchSettings.fiMatchFactor());

        ensureViewCreated();
        for (LeafReaderContext leafReaderContext: queryInfo.index().reader().leaves()) {
            spansReaders.add(new SpansReader(
                weight,
                leafReaderContext,
                this.hitQueryContext,
                getSpansReaderStrategy(leafReaderContext),
                this.requestedHitsToProcess,
                this.requestedHitsToCount,
                    hitsStats,
                    docsStats
            ));
        }

        if (spansReaders.isEmpty())
            setDone();
    }

    void ensureViewCreated() {
        // Global view on our segment hits
        hitsView = lazyHitsView = new HitsFromQuery(this, hitQueryContext.getMatchInfoDefs(), numThreads, getExecutorService());
    }

    protected SpansReader.Strategy getSpansReaderStrategy(LeafReaderContext lrc) {
        // Return a strategy that will add hits to the segment hits and maintain the global view.
        return lazyHitsView.getSpansReaderStrategy(lrc);
    }

    @Override
    public boolean ensureResultsRead(long number) {
        // clamp number to [current requested, number, max. requested], defaulting to max if number < 0
        final long clampedNumber = number < 0 ? maxHitsToCount : Math.min(number + FETCH_HITS_MIN, maxHitsToCount);

        if (allSourceSpansFullyRead || globalHitsSoFar() >= clampedNumber) {
            return globalHitsSoFar() >= number;
        }

        // NOTE: we first update to process, then to count. If we do it the other way around, and spansReaders
        //       are running, they might check in between the two statements and conclude that they don't need to save
        //       hits anymore, only count them.
        this.requestedHitsToProcess.getAndUpdate(c -> Math.max(Math.min(clampedNumber, maxHitsToProcess), c)); // update process
        this.requestedHitsToCount.getAndUpdate(c -> Math.max(clampedNumber, c)); // update count

        boolean hasLock = false;
        List<? extends Future<?>> pendingResults = null;
        try {
            while (!ensureHitsReadLock.tryLock(HIT_POLLING_TIME_MS, TimeUnit.MILLISECONDS)) {
                /*
                * Another thread is already working on hits, we don't want to straight up block until it's done,
                * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction.
                * So instead poll our own state, then if we're still missing results after that just count them ourselves
                */
                if (allSourceSpansFullyRead || (globalHitsSoFar() >= clampedNumber)) {
                    return globalHitsSoFar() >= number;
                }
            }
            hasLock = true;

            // This is the blocking portion, start worker threads, then wait for them to finish.
            final ExecutorService executorService = getExecutorService();

            // Distribute the SpansReaders over the threads.
            // Make sure the number of documents per segment is roughly equal for each thread.
            Function<SpansReader, Long> sizeGetter = spansReader ->
                    spansReader.leafReaderContext == null ? 0 : (long) spansReader.leafReaderContext.reader().maxDoc();
            pendingResults = makeEqualGroups(spansReaders, sizeGetter, numThreads).stream()
                .map(list -> executorService.submit(() -> list.forEach(SpansReader::run)))
                .toList();

            // Wait for workers to complete.
            // This will throw InterrupedException if this (HitsFromQuery) thread is interruped while waiting.
            // NOTE: the worker will not automatically abort, so we should also interrupt our workers should that happen.
            // The workers themselves won't ever throw InterruptedException, it would be wrapped in ExecutionException.
            // (Besides, we're the only thread that can call interrupt() on our worker anyway, and we don't ever do that.
            //  Technically, it could happen if the Executor were to shut down, but it would still result in an ExecutionException anyway.)
            for (Future<?> p : pendingResults) 
                p.get();
        } catch (InterruptedException e) {
            // We were interrupted while waiting for workers to finish.
            // If we were the thread that created the workers, cancel them. (this isn't always the case, we may have been interrupted during self-polling phase)
            // For the TermsReaders that aren't done yet, the next time this function is called we'll just create new Runnables/Futures of them.
            Thread.currentThread().interrupt(); // preserve interrupted status
            if (pendingResults != null) {
                for (Future<?> p : pendingResults) 
                    p.cancel(true);
            }
            throw new InterruptedSearch(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException rte)
                throw rte;
            else
                throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            // something unforseen happened in our thread
            // Should generally never happen unless there's a bug or something catastrophic happened.
            throw new IllegalStateException(e);
        } finally {
            // Don't do this unless we're the thread that's actually using the SpansReaders.
            if (hasLock) {
                // Remove all SpansReaders that have finished.
                spansReaders.removeIf(spansReader -> spansReader.isDone);
                if (spansReaders.isEmpty())
                    setDone(); // all spans have been read, so we're done
                ensureHitsReadLock.unlock();
            }
        }
        return globalHitsSoFar() >= number;
    }

    @Override
    public ResultsStatsPassive resultsStats() {
        return hitsStats;
    }

    @Override
    public ResultsStatsPassive docsStats() {
        return docsStats;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "hitQueryContext=" + hitQueryContext +
                ", hitsStats=" + hitsStats +
                ", docsStats=" + docsStats +
                '}';
    }

    void setDone() {
        allSourceSpansFullyRead = true;
        hitsStats.setDone();
        docsStats.setDone();
    }

    protected ExecutorService getExecutorService() {
        BlackLabIndex index = queryInfo().index();
        final ExecutorService executorService = numThreads >= 2
                ? index.blackLab().searchExecutorService()
                : new CurrentThreadExecutorService();
        return executorService;
    }

    public MatchInfoDefs getMatchInfoDefs() {
        return hitQueryContext.getMatchInfoDefs();
    }
}
