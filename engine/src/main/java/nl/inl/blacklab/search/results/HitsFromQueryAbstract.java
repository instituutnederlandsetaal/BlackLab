package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.util.CurrentThreadExecutorService;

public abstract class HitsFromQueryAbstract extends HitsAbstract {

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

    public HitsFromQueryAbstract(QueryInfo queryInfo, HitsInternalMutable hits, SearchSettings searchSettings) {
        super(queryInfo, hits, true);
        maxHitsToProcess = searchSettings.maxHitsToProcess();
        maxHitsToCount = searchSettings.maxHitsToCount();
        hitQueryContext = new HitQueryContext(queryInfo.index(), null, queryInfo.field()); // each spans will get a copy
        hitsInternalMutable.setMatchInfoDefs(hitQueryContext.getMatchInfoDefs());
        hitsStats = new ResultsStatsPassive(new ResultsAwaiterHits(this), maxHitsToProcess, maxHitsToCount);
        docsStats = new ResultsStatsPassive(new ResultsAwaiterDocs(this));
        numThreads = Math.max(queryInfo.index().blackLab().maxThreadsPerSearch(), 1);
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
}
