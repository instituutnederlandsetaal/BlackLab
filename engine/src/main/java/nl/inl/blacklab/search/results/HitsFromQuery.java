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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.util.CurrentThreadExecutorService;

public class HitsFromQuery extends HitsMutable {

    /** If another thread is busy fetching hits and we're monitoring it, how often should we check? */
    private static final int HIT_POLLING_TIME_MS = 50;

    protected final AtomicLong globalDocsProcessed = new AtomicLong();
    protected final AtomicLong globalDocsCounted = new AtomicLong();
    protected final AtomicLong globalHitsProcessed = new AtomicLong();
    protected final AtomicLong globalHitsCounted = new AtomicLong();
    /** Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1 */
    protected final AtomicLong requestedHitsToProcess = new AtomicLong();
    /** Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1 */
    protected final AtomicLong requestedHitsToCount = new AtomicLong();
    /** Configured upper limit of requestedHitsToProcess, to which it will always be clamped. */
    protected final long maxHitsToProcess;
    /** Configured upper limit of requestedHitsToCount, to which it will always be clamped. */
    protected final long maxHitsToCount;

    // state
    protected final HitQueryContext hitQueryContext;
    protected final Lock ensureHitsReadLock = new ReentrantLock();
    protected final List<SpansReader> spansReaders = new ArrayList<>();
    protected boolean allSourceSpansFullyRead = false;

    protected HitsFromQuery(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        // NOTE: we explicitly construct HitsInternal so they're writeable
        super(queryInfo.optOverrideField(sourceQuery), HitsInternal.create(-1, true, true), null);
        hitQueryContext = new HitQueryContext(null, sourceQuery.getField(), new MatchInfoDefs()); // each spans will get a copy
        QueryTimings timings = queryInfo().timings();
        timings.start();
        final BlackLabIndex index = queryInfo.index();
        final IndexReader reader = index.reader();
        BLSpanQuery optimizedQuery;

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

        try {
            // Override FI match threshold? (debug use only!)
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

                optimizedQuery = sourceQuery.optimize(reader);
                if (traceOptimization)
                    logger.debug("Query after optimize(): " + optimizedQuery);

                optimizedQuery = optimizedQuery.rewrite(reader);
                if (traceOptimization)
                    logger.debug("Query after rewrite(): " + optimizedQuery);

                // Restore previous FI match threshold
                if (searchSettings.fiMatchFactor() != -1) {
                    ClauseCombinerNfa.setNfaThreshold(oldFiMatchValue);
                }
            }
            timings.record("rewrite");

            // This call can take a long time
            BLSpanWeight weight = optimizedQuery.createWeight(index.searcher(), ScoreMode.COMPLETE_NO_SCORES, 1.0f);
            timings.record("createWeight");

            // We must always initialize one spansReader upfront, so global state for Capture Groups and context are created.
            // We then store get these global objects from the initialized SpansReader, and pass them to the rest of the (stil uninitialized) SpansReaders.
            // Yes, this is a smell. If/when refactoring this, don't forget to update the comments in SpansReader accordingly.
            boolean hasInitialized = false;
            for (LeafReaderContext leafReaderContext : reader.leaves()) {
                SpansReader spansReader = new SpansReader(
                    weight,
                    leafReaderContext,
                    this.hitQueryContext,
                    this.hitsInternalMutable,
                        this.globalDocsProcessed,
                    this.globalDocsCounted,
                    this.globalHitsProcessed,
                    this.globalHitsCounted,
                    this.requestedHitsToProcess,
                    this.requestedHitsToCount
                );
                spansReaders.add(spansReader);

                if (!hasInitialized) {
                    // We haven't initialized the HitQueryContext and CapturedGroups yet,
                    // because this is the first SpansReader (or at least the first one that
                    // contains at least one hit). Initialize them now.

                    // NOTE: this will initialize our HitQueryContext with any capture group names!
                    spansReader.initialize();
                    if (spansReader.isDone)
                        continue;
                    HitQueryContext hitQueryContextForThisSpans = spansReader.getHitContext();

                    // Now figure out if we have capture groups
                    // Needs to be null if unused!
                    if (hitQueryContextForThisSpans.numberOfMatchInfos() > 0) {
                        matchInfoDefs = hitQueryContextForThisSpans.getMatchInfoDefs();
                    }

                    hasInitialized = true;
                }
            }

            if (spansReaders.isEmpty())
                allSourceSpansFullyRead = true;
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @Override
    protected void ensureResultsRead(long number) {
        final long clampedNumber = number < 0 ? maxHitsToCount : Math.min(number, maxHitsToCount);

        if (allSourceSpansFullyRead || (hitsInternalMutable.size() >= clampedNumber)) {
            return;
        }

        // clamp number to [current requested, number, max. requested], defaulting to max if number < 0
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

            final AtomicLong i = new AtomicLong();
            pendingResults = spansReaders
                .stream()
                .collect(Collectors.groupingBy(sr -> i.getAndIncrement() % numThreads)) // subdivide the list, one sublist per thread to use (one list in case of single thread).
                .values()
                .stream()
                .map(list -> executorService.submit(() -> list.forEach(SpansReader::run))) // now submit one task per sublist
                .collect(Collectors.toList()); // gather the futures

            // Wait for workers to complete.
            // This will throw InterrupedException if this (HitsFromQueryParallel) thread is interruped while waiting.
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
                this.allSourceSpansFullyRead = spansReaders.isEmpty();
                ensureHitsReadLock.unlock();
            }
        }
    }

    @Override
    public MaxStats maxStats() {
        return new MaxStats(this.globalHitsCounted.get() >= this.maxHitsToProcess, this.globalHitsCounted.get() >= this.maxHitsToCount);
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return allSourceSpansFullyRead || (maxStats().hitsCountedExceededMaximum() && maxStats().hitsProcessedExceededMaximum());
    }

    @Override
    protected long docsCountedSoFar() {
        return this.globalDocsCounted.get();
    }

    @Override
    protected long docsCountedTotal() {
        ensureAllResultsRead();
        return this.globalDocsCounted.get();
    }

    @Override
    protected long docsProcessedSoFar() {
        return this.globalDocsProcessed.get();
    }

    @Override
    protected long docsProcessedTotal() {
        ensureAllResultsRead();
        return this.globalDocsProcessed.get();
    }

    @Override
    protected long hitsCountedSoFar() {
        return this.globalHitsCounted.get();
    }

    @Override
    protected long hitsCountedTotal() {
        ensureAllResultsRead();
        return this.globalHitsCounted.get();
    }

    @Override
    protected long hitsProcessedSoFar() {
        return this.globalHitsProcessed.get();
    }

    @Override
    protected long hitsProcessedTotal() {
        ensureAllResultsRead();
        return this.globalHitsProcessed.get();
    }

    @Override
    protected long resultsCountedSoFar() {
        return hitsCountedSoFar();
    }

    @Override
    protected long resultsCountedTotal() {
        return hitsCountedTotal();
    }

    @Override
    protected long resultsProcessedSoFar() {
        return hitsProcessedSoFar();
    }

    @Override
    protected long resultsProcessedTotal() {
        return hitsProcessedTotal();
    }

    @Override
    public boolean hasAscendingLuceneDocIds() {
        return true; // not strictly true, but per-leafreader it is true, which is what matters (for DocValues).
    }
}
