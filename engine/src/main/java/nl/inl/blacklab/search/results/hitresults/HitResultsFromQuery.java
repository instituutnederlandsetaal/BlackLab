package nl.inl.blacklab.search.results.hitresults;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsFromPublishers;
import nl.inl.blacklab.search.results.hits.fetch.HitPublisher;
import nl.inl.blacklab.search.results.hits.fetch.HitPublisherSpans;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;

public class HitResultsFromQuery extends HitResultsAbstract {

    /** Testing suggests this to be a good number of threads for processing hits in parallel, on average.
     *  Simple queries are not CPU-bound at all; more complex queries are, and thus benefit from more threads,
     *  up to a point. Too many threads just add overhead.
     */
    public static final int OPTIMAL_NUMBER_OF_THREADS = 3;

    public static void setNumberOfThreads(int numberOfThreads) {
        HitResultsFromQuery.numberOfThreads = numberOfThreads;
    }

    private static int numberOfThreads = OPTIMAL_NUMBER_OF_THREADS;

    /** Global view on our segment hits */
    private final HitsFromPublishers hits;

    protected HitResultsFromQuery(QueryInfo queryInfo, BLSpanQuery sourceQuery,
            SearchSettings searchSettings) {
        super(queryInfo.optOverrideField(sourceQuery));
        sourceQuery.setQueryInfo(queryInfo);
        if (searchSettings == null)
            searchSettings = SearchSettings.UNLIMITED;
        ResultsStatsPassive hitsStats = new ResultsStatsPassive(searchSettings.maxHitsToProcess(), searchSettings.maxHitsToCount());
        ResultsStatsPassive docsStats = new ResultsStatsPassive();
        List<HitPublisher> publishers = getHitPublishers(queryInfo, sourceQuery, searchSettings, hitsStats, docsStats);
        hits = new HitsFromPublishers(publishers, searchSettings);
    }

    public static List<HitPublisher> getHitPublishers(QueryInfo queryInfo, BLSpanQuery sourceQuery,
            SearchSettings searchSettings, ResultsStatsPassive hitsStats, ResultsStatsPassive docsStats) {
        List<HitPublisher> publishers = new ArrayList<>();
        BlackLabIndex index = queryInfo.index();
        BLSpanWeight weight = rewriteAndCreateWeight(index, sourceQuery, searchSettings.fiMatchFactor());
        int nThreads = Math.min(numberOfThreads, Math.max(index.blackLab().maxThreadsPerSearch(), 1));

        ExecutorService service = new SearchPool(index.blackLab().searchExecutorService(), nThreads);
        HitQueryContext hitQueryContext = new HitQueryContext(index, null, queryInfo.field());
        for (LeafReaderContext lrc: index.reader().leaves()) {
            publishers.add(new HitPublisherSpans(lrc, weight, hitQueryContext, service, hitsStats, docsStats, true));
        }
        return publishers;
    }

    /**
     * Call optimize() and rewrite() on the source query, and create a weight for it.
     *
     * @param sourceQuery   the source query to optimize and rewrite
     * @param fiMatchFactor override FI match threshold (debug use only, -1 means no override)
     * @return the weight for the optimized/rewritten query
     */
    protected static BLSpanWeight rewriteAndCreateWeight(BlackLabIndex index, BLSpanQuery sourceQuery,
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
    public long numberOfResultObjects() {
        return hits.sizeSoFar();
    }

    @Override
    public Hits getHits() {
        return hits;
    }

    @Override
    public ResultsStats resultsStats() {
        return hits.resultsStats();
    }

    @Override
    public ResultsStats docsStats() {
        return hits.docsStats();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public MatchInfoDefs getMatchInfoDefs() {
        return hits.matchInfoDefs();
    }

}
