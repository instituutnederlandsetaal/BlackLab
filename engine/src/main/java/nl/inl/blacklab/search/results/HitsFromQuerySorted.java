package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.util.CurrentThreadExecutorService;

public class HitsFromQuerySorted extends HitsAbstract {

    /** Configured upper limit of requestedHitsToProcess, to which it will always be clamped. */
    protected final long maxHitsToProcess;

    /** Configured upper limit of requestedHitsToCount, to which it will always be clamped. */
    protected final long maxHitsToCount;

    /** Query context, keeping track of e.g. match info defitions */
    protected final HitQueryContext hitQueryContext;

    /** Our query weight */
    private final BLSpanWeight weight;

    /** What to sort our hits by */
    private final HitProperty sortBy;

    /** Sorted hits from each segment to merge */
    private List<HitsInternal> segmentHits;

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

    protected HitsFromQuerySorted(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings, HitProperty sortBy) {
        // NOTE: we explicitly construct HitsInternal so they're writeable
        super(queryInfo.optOverrideField(sourceQuery), false);
        this.sortBy = sortBy;
        hitQueryContext = new HitQueryContext(queryInfo.index(), null, queryInfo.field()); // each spans will get a copy
        maxHitsToCount = searchSettings.maxHitsToCount();
        maxHitsToProcess = searchSettings.maxHitsToProcess();
        hitsStats = new ResultsStatsPassive(new ResultsAwaiterHits(this), maxHitsToProcess, maxHitsToCount);
        docsStats = new ResultsStatsPassive(new ResultsAwaiterDocs(this));
        weight = rewriteAndCreateWeight(queryInfo, sourceQuery, searchSettings.fiMatchFactor());
    }

    protected void ensureResultsRead(long number) {
        if (segmentHits == null && number > 0) {
            segmentHits = gatherFromAllSegments(sortBy);
        }

        // TODO: we need to merge hits from the segments here.
        //       how do we avoid keeping a lot of memory occupied?
    }

    private List<HitsInternal> gatherFromAllSegments(HitProperty sortBy) {
        List<Future<HitsInternal>> pendingResults = new ArrayList<>();
        List<HitsInternal> segmentHits = new ArrayList<>();
        try {

            // This is the blocking portion, start worker threads, then wait for them to finish.
            BlackLabIndex index = queryInfo().index();
            final int numThreads = Math.max(index.blackLab().maxThreadsPerSearch(), 1);
            final ExecutorService executorService = numThreads >= 2
                    ? index.blackLab().searchExecutorService()
                    : new CurrentThreadExecutorService();

            for (LeafReaderContext leafReaderContext: index.reader().leaves()) {
                Future<HitsInternal> f = executorService.submit(() -> {
                    // Gather and sort all hits for this segment.
                    HitsInternalMutable hits = HitsInternal.gatherAll(weight, leafReaderContext, hitQueryContext);
                    hits.sort(sortBy);
                    return hits;
                });
                pendingResults.add(f);
            }
            for (Future<HitsInternal> p: pendingResults)
                segmentHits.add(p.get());
            return segmentHits;
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
        }
    }

    private static BLSpanWeight rewriteAndCreateWeight(QueryInfo queryInfo, BLSpanQuery sourceQuery,
            long fiMatchFactor) {
        QueryTimings timings = queryInfo.timings();
        timings.start();

        // Override FI match threshold? (debug use only!)
        BLSpanWeight weight;
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
            weight = optimizedQuery.createWeight(queryInfo.index().searcher(), ScoreMode.COMPLETE_NO_SCORES, 1.0f);
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
        timings.record("createWeight");
        return weight;
    }

    @Override
    public String toString() {
        return "HitsFromQuerySorted{" +
                "hitQueryContext=" + hitQueryContext +
                ", hitsStats=" + hitsStats +
                ", docsStats=" + docsStats +
                '}';
    }

    @Override
    public WindowStats windowStats() {
        return null;
    }
}
