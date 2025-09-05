package nl.inl.blacklab.search.results.hits.fetch;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.results.SearchSettings;

/**
 * Fetches hits from a query in parallel.
 */
public class HitFetcherQuery extends HitFetcherAbstract {

    private static final Logger logger = LogManager.getLogger(HitFetcherQuery.class);

    private final BLSpanWeight weight;

    public HitFetcherQuery(
            BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        super(sourceQuery.getAnnotatedField(), searchSettings);
        this.weight = rewriteAndCreateWeight(sourceQuery, searchSettings.fiMatchFactor());
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
    public void fetchHits(HitFilter filter, HitCollector hitCollector) {
        super.fetchHits(filter, hitCollector);
        for (LeafReaderContext lrc: index.reader().leaves()) {
            // Hit processor: gathers the hits from this segment and (when there's enough) adds them
            // to the global view.

            // Spans reader: fetch hits from segment and feed them to the hit processor.
            HitFetcherSegment.State state = getState(hitCollector, lrc, filter);
            segmentReaders.add(new HitFetcherSegmentQuery(weight, state));
        }
        if (segmentReaders.isEmpty()) {
            setDone();
        }
    }
}
