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

        public HitsWithoutResults(QueryInfo queryInfo, long totalHits, long totalDocuments, boolean maxHitsProcessed, boolean maxHitsCounted) {
            super(queryInfo, true);
            this.hitsCounted = totalHits;
            this.docsCounted = totalDocuments;
            this.docsRetrieved = 0;

            this.maxHitsProcessed = maxHitsProcessed;
            this.maxHitsCounted = maxHitsCounted;
        }

        @Override
        protected void ensureResultsRead(long number) {
            // NOP
        }

        @Override
        public boolean doneProcessingAndCounting() {
            return true;
        }

        @Override
        public MaxStats maxStats() {
            return new MaxStats(maxHitsProcessed, maxHitsCounted);
        }
    }

    public HitGroupWithoutResults(QueryInfo queryInfo, PropertyValue groupIdentity, long totalHits, int totalDocuments, boolean maxHitsProcessed, boolean maxHitsCounted) {
        super(groupIdentity, new HitsWithoutResults(queryInfo, totalHits, totalDocuments, maxHitsCounted, maxHitsProcessed), totalHits);
    }
}
