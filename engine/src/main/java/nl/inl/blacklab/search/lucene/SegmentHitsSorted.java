package nl.inl.blacklab.search.lucene;

import nl.inl.blacklab.resultproperty.HitProperty;

/**
 * Sort hits from a single index segment.
 */
class SegmentHitsSorted extends SegmentHitsFetchAll {

    /**
     * Constructs a SpansEdge.
     *
     * @param clause the clause to get and sort hits from
     */
    public SegmentHitsSorted(BLSpans clause, HitProperty sortBy, HitQueryContext context) {
        super(clause, context);
        hits.sort(sortBy);
    }

}
