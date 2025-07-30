package nl.inl.blacklab.search.results;

import nl.inl.blacklab.resultproperty.PropertyValue;

/**
 * A hit group that doesn't store any actual hits.
 */
public class HitGroupWithoutResults extends HitGroup {

    /**
     * A Hits object that only stores statistics about a set of hits, not the actual hits themselves (because we don't need them).
     */
    private static class HitsWithoutResults extends HitsAbstract {
        protected final boolean maxHitsProcessed;
        protected final boolean maxHitsCounted;

        private final ResultsStatsSaved hitsStats;
        private final ResultsStatsSaved docsStats;

        public HitsWithoutResults(QueryInfo queryInfo, long totalHits, long totalDocuments, boolean maxHitsProcessed, boolean maxHitsCounted) {
            super(queryInfo, HitsInternal.empty(queryInfo.field(), null), false);

            this.maxHitsProcessed = maxHitsProcessed;
            this.maxHitsCounted = maxHitsCounted;

            MaxStats maxStats = MaxStats.get(maxHitsProcessed, maxHitsCounted);
            hitsStats = new ResultsStatsSaved(0, totalHits, maxStats);
            docsStats = new ResultsStatsSaved(0, totalDocuments, maxStats);
        }

        @Override
        protected boolean ensureResultsRead(long number) {
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
                    "maxHitsProcessed=" + maxHitsProcessed +
                    ", maxHitsCounted=" + maxHitsCounted +
                    ", hitsStats=" + hitsStats +
                    ", docsStats=" + docsStats +
                    '}';
        }
    }

    public HitGroupWithoutResults(QueryInfo queryInfo, PropertyValue groupIdentity, long totalHits, int totalDocuments, boolean maxHitsProcessed, boolean maxHitsCounted) {
        super(groupIdentity, new HitsWithoutResults(queryInfo, totalHits, totalDocuments, maxHitsCounted, maxHitsProcessed), totalHits);
    }

    @Override
    public String toString() {
        return "HitGroupWithoutResults{" +
                "groupIdentity=" + groupIdentity +
                '}';
    }
}
