package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Resolve e.g. Q within rcapture(R+) with the shortest possible match for R+.
 *
 * Used to implement context=s where (relations in) multiple sentences may have to be
 * captured because the match crosses sentence boundaries.
 */
public class SpanQueryWithinShortestRepetition extends BLSpanQueryAbstract {

    public static SpanGuarantees createGuarantees(SpanGuarantees producer) {
        return new SpanGuaranteesAdapter(producer) {
            @Override
            public boolean hitsStartPointSorted() {
                return true;
            }
        };
    }

    private final String captureRelsAs;

    /** How to adjust the leading edge of the producer hits while matching */
    int adjustLeading;

    /** How to adjust the trailing edge of the producer hits while matching */
    int adjustTrailing;

    /**
     * Produce hits that match filterUnit+ hits.
     *
     * @param producer hits we may be interested in
     * @param filterUnit how we determine what producer hits we're interested in
     */
    public SpanQueryWithinShortestRepetition(QueryInfo queryInfo, BLSpanQuery producer, BLSpanQuery filterUnit,
            AnnotationSensitivity relationField, String captureRelsAs) {
        this(queryInfo, producer, filterUnit, relationField, captureRelsAs, 0, 0);
    }

    /**
     * Produce hits that match filterUnit+ hits.
     *
     * @param producer       hits we may be interested in
     * @param filterUnit     how we determine what producer hits we're interested in
     * @param adjustLeading  how to adjust the leading edge of the producer hits while
     *                       matching
     * @param adjustTrailing how to adjust the trailing edge of the producer hits while
     *                       matching
     */
    public SpanQueryWithinShortestRepetition(QueryInfo queryInfo, BLSpanQuery producer, BLSpanQuery filterUnit,
            AnnotationSensitivity relationField, String captureRelsAs, int adjustLeading, int adjustTrailing) {
        this(producer, filterUnit, new SpanQueryRelations(queryInfo, relationField,
                XFRelations.REGEX_RELATIONS_ALL_CLASSES_ALL_TYPE, Collections.emptyMap(),
                SpanQueryRelations.Direction.BOTH_DIRECTIONS, RelationInfo.SpanMode.FULL_SPAN,
                "", false, null), captureRelsAs, adjustLeading, adjustTrailing);
    }

    /**
     * Produce hits that match filterUnit+ hits.
     *
     * @param producer       hits we may be interested in
     * @param filterUnit     how we determine what producer hits we're interested in
     * @param adjustLeading  how to adjust the leading edge of the producer hits while
     *                       matching
     * @param adjustTrailing how to adjust the trailing edge of the producer hits while
     *                       matching
     */
    public SpanQueryWithinShortestRepetition(BLSpanQuery producer, BLSpanQuery filterUnit, BLSpanQuery relations,
            String captureRelsAs, int adjustLeading, int adjustTrailing) {
        super(producer, filterUnit, relations);
        this.adjustLeading = adjustLeading;
        this.adjustTrailing = adjustTrailing;
        this.guarantees = createGuarantees(producer.guarantees());

        // For (simulated) rcapture()
        this.captureRelsAs = captureRelsAs;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery producer = clauses.get(0).rewrite(reader);
        BLSpanQuery filter = clauses.get(1).rewrite(reader);
        BLSpanQuery relations = clauses.get(2).rewrite(reader);
        if (producer != clauses.get(0) || filter != clauses.get(1)) {
            SpanQueryWithinShortestRepetition result = new SpanQueryWithinShortestRepetition(producer, filter,
                    relations, captureRelsAs, adjustLeading, adjustTrailing);
            result.adjustLeading = adjustLeading;
            result.adjustTrailing = adjustTrailing;
            return result;
        }
        return this;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight prodWeight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        BLSpanWeight filterWeight = clauses.get(1).createWeight(searcher, scoreMode, boost);
        BLSpanWeight relationsWeight = clauses.get(2).createWeight(searcher, scoreMode, boost);
        Map<Term, TermStates> contexts = scoreMode.needsScores() ? getTermStates(prodWeight, filterWeight) : null;
        return new SpanWeightWithinShortestRepetition(prodWeight, filterWeight, relationsWeight, searcher, contexts, boost);
    }

    class SpanWeightWithinShortestRepetition extends BLSpanWeight {

        final BLSpanWeight prodWeight, filterWeight, relationsWeight;

        public SpanWeightWithinShortestRepetition(BLSpanWeight prodWeight, BLSpanWeight filterWeight,
                BLSpanWeight relationsWeight, IndexSearcher searcher,
                Map<Term, TermStates> terms, float boost) throws IOException {
            super(SpanQueryWithinShortestRepetition.this, searcher, terms, boost);
            this.prodWeight = prodWeight;
            this.filterWeight = filterWeight;
            this.relationsWeight = relationsWeight;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return prodWeight.isCacheable(ctx) && filterWeight.isCacheable(ctx) && relationsWeight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            prodWeight.extractTermStates(contexts);
            filterWeight.extractTermStates(contexts);
            relationsWeight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spansProd = prodWeight.getSpans(context, requiredPostings);
            if (spansProd == null)
                return null;
            BLSpans spansFilter = filterWeight.getSpans(context, requiredPostings);
            if (spansFilter == null) {
                // No filter hits. No producer hits can match.
                return null;
            }
            BLSpans relations = relationsWeight.getSpans(context, requiredPostings);
            return new SpansWithinShortestRepetition(spansProd, spansFilter, relations,
                    captureRelsAs, adjustLeading, adjustTrailing);
        }
    }

    @Override
    public String toString(String field) {
        String adj = (adjustLeading != 0 || adjustTrailing != 0 ? ", " + adjustLeading + ", " + adjustTrailing : "");
        return "WITHIN-SHORTEST-REP(" + clausesToString(field) + ", " + adj + ")";
    }

    public SpanQueryWithinShortestRepetition copy() {
        return new SpanQueryWithinShortestRepetition(clauses.get(0), clauses.get(1),
                clauses.get(2), captureRelsAs, adjustLeading, adjustTrailing);
    }

    /**
     * Adjust the leading edge of the producer hits for matching only.
     *
     * That is, the original producer hit is returned, not the adjusted one.
     *
     * @param delta how to adjust the edge
     */
    public void adjustLeading(int delta) {
        adjustLeading += delta;
    }

    /**
     * Adjust the trailing edge of the producer hits for matching only.
     *
     * That is, the original producer hit is returned, not the adjusted one.
     *
     * @param delta how to adjust the edge
     */
    public void adjustTrailing(int delta) {
        adjustTrailing += delta;
    }

    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence();
    }

    @Override
    public BLSpanQuery noEmpty() {
        return new SpanQueryWithinShortestRepetition(clauses.get(0).noEmpty(), clauses.get(1),
                clauses.get(2), captureRelsAs, adjustLeading, adjustTrailing);
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clauses.get(0).reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.get(0).forwardMatchingCost();
    }

    @Override
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        return clause.guarantees().hitsAllSameLength();
    }

    @Override
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clause, boolean addToRight) {
        if (!clause.guarantees().hitsAllSameLength())
            throw new IllegalStateException("Trying to internalize non-constant-length clause: " + clause);
        // Create a new position filter query with a constant-length clause added to our producer.
        // adjustLeading and adjustTrailing are updated according to the clause's length, so it is not
        // actually filtered.
        BLSpanQuery producer = clauses.get(0);
        SpanQuerySequence seq = SpanQuerySequence.sequenceInternalize(producer, clause, addToRight);
        if (addToRight)
            return new SpanQueryWithinShortestRepetition(seq, clauses.get(1), clauses.get(2),
                    captureRelsAs, adjustLeading, adjustTrailing - clause.guarantees().hitsLengthMin());
        return new SpanQueryWithinShortestRepetition(seq, clauses.get(1), clauses.get(2),
                captureRelsAs, adjustLeading + clause.guarantees().hitsLengthMin(), adjustTrailing);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + adjustLeading;
        result = prime * result + adjustTrailing;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        SpanQueryWithinShortestRepetition other = (SpanQueryWithinShortestRepetition) obj;
        if (adjustLeading != other.adjustLeading)
            return false;
        if (adjustTrailing != other.adjustTrailing)
            return false;
        return true;
    }
}
