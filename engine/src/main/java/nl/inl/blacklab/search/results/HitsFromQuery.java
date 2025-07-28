package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.util.CurrentThreadExecutorService;

public class HitsFromQuery extends HitsMutable {

    /** If another thread is busy fetching hits and we're monitoring it, how often should we check? */
    private static final int HIT_POLLING_TIME_MS = 50;

    /** Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1 */
    protected final AtomicLong requestedHitsToProcess = new AtomicLong();

    /** Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1 */
    protected final AtomicLong requestedHitsToCount = new AtomicLong();

    /** Configured upper limit of requestedHitsToProcess, to which it will always be clamped. */
    protected final long maxHitsToProcess;

    /** Configured upper limit of requestedHitsToCount, to which it will always be clamped. */
    protected final long maxHitsToCount;

    /** Query context, keeping track of e.g. match info defitions */
    protected final HitQueryContext hitQueryContext;

    /** Used to make sure that only 1 thread can be fetching hits at a time. */
    protected final Lock ensureHitsReadLock = new ReentrantLock();

    /** Objects getting the actual hits from each index segment and adding them to the global results list. */
    protected final List<SpansReader> spansReaders = new ArrayList<>();

    /** If true, we're done. */
    protected boolean allSourceSpansFullyRead = false;

    /** Keeps track of hits encountered. */
    protected final ResultsStatsPassive hitsStats;

    /** Keeps track of docs encountered. */
    protected final ResultsStatsPassive docsStats;

    @Override
    public ResultsStats resultsStats() {
        return hitsStats;
    }

    @Override
    public ResultsStats docsStats() {
        return docsStats;
    }

    protected HitsFromQuery(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        // NOTE: we explicitly construct HitsInternal so they're writeable
        super(queryInfo.optOverrideField(sourceQuery),
                HitsInternal.create(queryInfo.optOverrideField(sourceQuery).field(),
                        null, -1, true, true));
        hitQueryContext = new HitQueryContext(queryInfo.index(), null, queryInfo.field()); // each spans will get a copy
        hitsInternalMutable.setMatchInfoDefs(hitQueryContext.getMatchInfoDefs());
        QueryTimings timings = queryInfo().timings();
        timings.start();

        // Ensure max. count >= max. process >= 0
        // After this both will be above 0 and process will never exceed count
        long configuredMaxHitsToCount = searchSettings.maxHitsToCount();
        long configuredMaxHitsToProcess = searchSettings.maxHitsToProcess();
        if (configuredMaxHitsToCount < 0)
            configuredMaxHitsToCount = Long.MAX_VALUE;
        if (configuredMaxHitsToProcess < 0 || configuredMaxHitsToProcess > configuredMaxHitsToCount)
            configuredMaxHitsToProcess = configuredMaxHitsToCount;
        this.maxHitsToProcess = configuredMaxHitsToProcess;
        this.maxHitsToCount = configuredMaxHitsToCount;

        hitsStats = new ResultsStatsPassive(new ResultsAwaiterHits(this), maxHitsToProcess, maxHitsToCount);
        docsStats = new ResultsStatsPassive(new ResultsAwaiterDocs(this));

        try {
            // Override FI match threshold? (debug use only!)
            BLSpanQuery optimizedQuery;
            synchronized (ClauseCombinerNfa.class) {
                long oldFiMatchValue = ClauseCombinerNfa.getNfaThreshold();
                if (searchSettings.fiMatchFactor() != -1) {
                    logger.debug("setting NFA threshold for this query to " + searchSettings.fiMatchFactor());
                    ClauseCombinerNfa.setNfaThreshold(searchSettings.fiMatchFactor());
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
                if (searchSettings.fiMatchFactor() != -1) {
                    ClauseCombinerNfa.setNfaThreshold(oldFiMatchValue);
                }
            }
            timings.record("rewrite");

            // This call can take a long time
            BLSpanWeight weight = optimizedQuery.createWeight(queryInfo.index().searcher(), ScoreMode.COMPLETE_NO_SCORES, 1.0f);
            timings.record("createWeight");

            for (LeafReaderContext leafReaderContext : queryInfo.index().reader().leaves()) {
                spansReaders.add(new SpansReader(
                    weight,
                    leafReaderContext,
                    this.hitQueryContext,
                    this.hitsInternalMutable,
                    this.requestedHitsToProcess,
                    this.requestedHitsToCount,
                    hitsStats,
                    docsStats
                ));
            }

            if (spansReaders.isEmpty())
                setDone();
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    void setDone() {
        allSourceSpansFullyRead = true;
        hitsStats.setDone();
        docsStats.setDone();
    }

    @Override
    protected void ensureResultsRead(long number) {
        // clamp number to [current requested, number, max. requested], defaulting to max if number < 0
        final long clampedNumber = number < 0 ? maxHitsToCount : Math.min(number + FETCH_HITS_MIN, maxHitsToCount);

        if (allSourceSpansFullyRead || (hitsInternalMutable.size() >= clampedNumber)) {
            return;
        }

        // NOTE: we first update to process, then to count. If we do it the other way around, and spansReaders
        //       are running, they might check in between the two statements and conclude that they don't need to save
        //       hits anymore, only count them.
        this.requestedHitsToProcess.getAndUpdate(c -> Math.max(Math.min(clampedNumber, maxHitsToProcess), c)); // update process
        this.requestedHitsToCount.getAndUpdate(c -> Math.max(clampedNumber, c)); // update count

        boolean hasLock = false;
        List<Future<?>> pendingResults = null;
        try {
            while (!ensureHitsReadLock.tryLock(HIT_POLLING_TIME_MS, TimeUnit.MILLISECONDS)) {
                /*
                * Another thread is already working on hits, we don't want to straight up block until it's done,
                * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction.
                * So instead poll our own state, then if we're still missing results after that just count them ourselves
                */
                if (allSourceSpansFullyRead || (hitsInternalMutable.size() >= clampedNumber)) {
                    return;
                }
            }
            hasLock = true;
            
            // This is the blocking portion, start worker threads, then wait for them to finish.
            final int numThreads = Math.max(queryInfo().index().blackLab().maxThreadsPerSearch(), 1);
            final ExecutorService executorService = numThreads >= 2
                    ? queryInfo().index().blackLab().searchExecutorService()
                    : new CurrentThreadExecutorService();

            // Distribute the SpansReaders over the threads.
            // E.g. if we have 10 SpansReaders and 3 threads, we will have
            // SpansReader 0, 3, 6 and 9 in thread 1, etc.
            // This way, each thread will get a roughly equal number of SpansReaders to run.
            final AtomicLong i = new AtomicLong();
            pendingResults = spansReaders
                .stream()
                .collect(Collectors.groupingBy(sr -> i.getAndIncrement() % numThreads)) // subdivide the list, one sublist per thread to use (one list in case of single thread).
                .values()
                .stream()
                .map(list -> executorService.submit(() -> list.forEach(SpansReader::run))) // now submit one task per sublist
                .collect(Collectors.toList()); // gather the futures

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
    }

    @Override
    public String toString() {
        return "HitsFromQuery{" +
                "hitQueryContext=" + hitQueryContext +
                ", hitsStats=" + hitsStats +
                ", docsStats=" + docsStats +
                '}';
    }
}
