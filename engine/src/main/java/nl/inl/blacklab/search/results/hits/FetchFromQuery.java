package nl.inl.blacklab.search.results.hits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.results.SearchSettings;

/**
 * Fetches hits from a query in parallel (using SpansReader).
 */
public class FetchFromQuery implements HitFetcher {

    /**
     * Testing reveals this to be a good number of threads for fetching hits in parallel.
     * More is not useful, and we can afford to use 4 threads as fetching hits is a very fast operation
     * compared to sort/group.
     */
    public static final int IDEAL_NUM_THREADS_FETCHING = 4;
    private static final Logger logger = LogManager.getLogger(FetchFromQuery.class);

    /**
     * If another thread is busy fetching hits and we're monitoring it, how often should we check?
     */
    private static final int HIT_POLLING_TIME_MS = 50;

    /**
     * Minimum number of hits to fetch in an ensureHitsRead() block.
     * <p>
     * This prevents locking again and again for a single hit when iterating.
     */
    private static final int FETCH_HITS_MIN = 20;

    /**
     * Max. number of threads to use for fetch, sort, group.
     */
    private final int maxThreadsPerOperation;

    /**
     * Configured upper limit of requestedHitsToProcess, to which it will always be clamped.
     */
    private final long maxHitsToProcess;

    /**
     * Configured upper limit of requestedHitsToCount, to which it will always be clamped.
     */
    private final long maxHitsToCount;

    /**
     * Used to make sure that only 1 thread can be fetching hits at a time.
     */
    private final Lock ensureHitsReadLock = new ReentrantLock();

    private final HitQueryContext hitQueryContext;

    /**
     * Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1
     */
    private final AtomicLong requestedHitsToProcess = new AtomicLong();

    /**
     * Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1
     */
    private final AtomicLong requestedHitsToCount = new AtomicLong();

    private final BLSpanWeight weight;

    private final SearchSettings searchSettings;

    private HitCollector hitCollector;

    private final BlackLabIndex index;

    /**
     * If true, we're done.
     */
    private boolean allSourceSpansFullyRead = false;

    /**
     * Objects getting the actual hits from each index segment and adding them to the global results list.
     */
    final List<SpansReader> spansReaders = new ArrayList<>();

    public FetchFromQuery(
            BLSpanQuery sourceQuery, SearchSettings searchSettings, AnnotatedField field) {
        this.index = field.index();
        this.searchSettings = searchSettings;
        hitQueryContext = new HitQueryContext(index, null, field); // each spans will get a copy
        maxThreadsPerOperation = Math.max(index.blackLab().maxThreadsPerSearch(), 1);
        maxHitsToProcess = searchSettings.maxHitsToProcess();
        maxHitsToCount = searchSettings.maxHitsToCount();
        this.weight = rewriteAndCreateWeight(sourceQuery, searchSettings.fiMatchFactor());
    }

    @Override
    public boolean ensureResultsReader(long number) {
        // clamp number to [current requested, number, max. requested], defaulting to max if number < 0
        final long clampedNumber = number < 0 ? maxHitsToCount : Math.min(number + FETCH_HITS_MIN, maxHitsToCount);

        if (isDone() || hitCollector.globalHitsSoFar() >= clampedNumber) {
            return hitCollector.globalHitsSoFar() >= number;
        }

        // NOTE: we first update to process, then to count. If we do it the other way around, and spansReaders
        //       are running, they might check in between the two statements and conclude that they don't need to save
        //       hits anymore, only count them.
        requestedHitsToProcess.getAndUpdate(
                c -> Math.max(Math.min(clampedNumber, maxHitsToProcess), c)); // update process
        requestedHitsToCount.getAndUpdate(c -> Math.max(clampedNumber, c)); // update count

        boolean hasLock = false;
        int numThreads = Math.min(IDEAL_NUM_THREADS_FETCHING, maxThreadsPerOperation);
        Parallel<SpansReader, Void> parallel = new Parallel<>(index, numThreads);
        try {
            while (!ensureHitsReadLock.tryLock(HIT_POLLING_TIME_MS, TimeUnit.MILLISECONDS)) {
                /*
                 * Another thread is already working on hits, we don't want to straight up block until it's done,
                 * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction.
                 * So instead poll our own state, then if we're still missing results after that just count them ourselves
                 */
                if (allSourceSpansFullyRead || (hitCollector.globalHitsSoFar() >= clampedNumber)) {
                    return hitCollector.globalHitsSoFar() >= number;
                }
            }
            hasLock = true;

            // This is the blocking portion, start worker threads, then wait for them to finish.

            // Distribute the SpansReaders over the threads.
            // Make sure the number of documents per segment is roughly equal for each thread.
            Function<SpansReader, Long> sizeGetter = spansReader ->
                    spansReader.lrc == null ? 0 : (long) spansReader.lrc.reader().maxDoc();
            List<Future<List<Void>>> pendingResults = parallel.forEach(spansReaders, sizeGetter,
                    l -> l.forEach(SpansReader::run));

            // Wait for workers to complete.
            // This will throw InterrupedException if this (HitsFromQuery) thread is interruped while waiting.
            // NOTE: the worker will not automatically abort, so we should also interrupt our workers should that happen.
            // The workers themselves won't ever throw InterruptedException, it would be wrapped in ExecutionException.
            // (Besides, we're the only thread that can call interrupt() on our worker anyway, and we don't ever do that.
            //  Technically, it could happen if the Executor were to shut down, but it would still result in an ExecutionException anyway.)
            //
            // If we're interrupted while waiting for workers to finish, and we were the thread that created the workers,
            // cancel them. (this isn't always the case, we may have been interrupted during self-polling phase)
            // For the TermsReaders that aren't done yet, the next time this function is called we'll just create new
            // Runnables/Futures of them.
            parallel.waitForAll(pendingResults);
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
                    hitCollector.setDone(); // all spans have been read, so we're done
                ensureHitsReadLock.unlock();
            }
        }
        return hitCollector.globalHitsSoFar() >= number;
    }

    /**
     * Call optimize() and rewrite() on the source query, and create a weight for it.
     *
     * @param sourceQuery   the source query to optimize and rewrite
     * @param fiMatchFactor override FI match threshold (debug use only, -1 means no override)
     * @return the weight for the optimized/rewritten query
     */
    protected BLSpanWeight rewriteAndCreateWeight(BLSpanQuery sourceQuery,
            long fiMatchFactor) {
        // Override FI match threshold? (debug use only!)
        try {
            BLSpanQuery optimizedQuery;
            synchronized (ClauseCombinerNfa.class) {
                long oldFiMatchValue = ClauseCombinerNfa.getNfaThreshold();
                if (fiMatchFactor != -1) {
                    logger.debug("setting NFA threshold for this query to {}", fiMatchFactor);
                    ClauseCombinerNfa.setNfaThreshold(fiMatchFactor);
                }

                boolean traceOptimization = BlackLab.config().getLog().getTrace().isOptimization();
                if (traceOptimization)
                    logger.debug("Query before optimize()/rewrite(): {}", sourceQuery);

                optimizedQuery = sourceQuery.optimize(index.reader());
                if (traceOptimization)
                    logger.debug("Query after optimize(): {}", optimizedQuery);

                optimizedQuery = optimizedQuery.rewrite(index.reader());
                if (traceOptimization)
                    logger.debug("Query after rewrite(): {}", optimizedQuery);

                // Restore previous FI match threshold
                if (fiMatchFactor != -1) {
                    ClauseCombinerNfa.setNfaThreshold(oldFiMatchValue);
                }
            }

            // This call can take a long time
            return optimizedQuery.createWeight(index.searcher(),
                    ScoreMode.COMPLETE_NO_SCORES, 1.0f);
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    @Override
    public boolean isDone() {
        return allSourceSpansFullyRead;
    }

    @Override
    public HitQueryContext getHitQueryContext() {
        return hitQueryContext;
    }

    @Override
    public void fetchHits(HitCollector hitCollector) {
        this.hitCollector = hitCollector;
        for (LeafReaderContext lrc: index.reader().leaves()) {
            // Hit processor: gathers the hits from this segment and (when there's enough) adds them
            // to the global view.
            HitProcessor hitProcessor = hitCollector.getHitProcessor(lrc);

            // Spans reader: fetch hits from segment and feed them to the hit processor.
            spansReaders.add(new SpansReader(
                    weight,
                    lrc,
                    hitQueryContext,
                    hitProcessor,
                    this.requestedHitsToProcess,
                    this.requestedHitsToCount,
                    hitCollector.resultsStats(),
                    hitCollector.docsStats()
            ));
        }
        if (spansReaders.isEmpty()) {
            allSourceSpansFullyRead = true;
            hitCollector.setDone();
        }
    }

    @Override
    public AnnotatedField field() {
        return hitQueryContext.getField();
    }

    @Override
    public SearchSettings getSearchSettings() {
        return searchSettings;
    }
}
