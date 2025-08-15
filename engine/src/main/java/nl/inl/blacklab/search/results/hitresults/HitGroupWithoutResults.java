package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.stats.MaxStats;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsSaved;

/**
 * A hit group that doesn't store any actual hits.
 */
public class HitGroupWithoutResults extends HitGroup {

    /**
     * A Hits object that only stores statistics about a set of hits, not the actual hits themselves (because we don't need them).
     */
    private static class HitResultsWithoutResults extends HitResultsWithHitsInternal {
        private final ResultsStatsSaved hitsStats;
        private final ResultsStatsSaved docsStats;

        public HitResultsWithoutResults(QueryInfo queryInfo, long totalHits, long totalDocuments, boolean maxHitsProcessed, boolean maxHitsCounted) {
            super(queryInfo, Hits.empty(queryInfo.field(), null));

            MaxStats maxStats = MaxStats.get(maxHitsProcessed, maxHitsCounted);
            hitsStats = new ResultsStatsSaved(0, totalHits, maxStats);
            docsStats = new ResultsStatsSaved(0, totalDocuments, maxStats);
        }

        @Override
        public boolean ensureResultsRead(long number) {
            return size() >= number; // all results have been read (always 0 in this case)
        }

        @Override
        public ResultsStats resultsStats() {
            return hitsStats;
        }

        @Override
        public ResultsStats docsStats() {
            return docsStats;
        }

        @Override
        public String toString() {
            return "HitsWithoutResults{" +
                    "hitsStats=" + hitsStats +
                    ", docsStats=" + docsStats +
                    '}';
        }
    }

    public HitGroupWithoutResults(QueryInfo queryInfo, PropertyValue groupIdentity, long totalHits, int totalDocuments, boolean maxHitsProcessed, boolean maxHitsCounted) {
        super(groupIdentity, new HitResultsWithoutResults(queryInfo, totalHits, totalDocuments, maxHitsCounted, maxHitsProcessed), totalHits);
    }

    @Override
    public String toString() {
        return "HitGroupWithoutResults{" +
                "groupIdentity=" + groupIdentity +
                '}';
    }
}
