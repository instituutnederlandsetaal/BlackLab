package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

/**
 * Filters hits from a producer query based on the hit positions of a filter
 * query. This allows us to do several things, such as: * find hits from the
 * producer that contain one or more hits from the filter * find hits from the
 * producer are contained by hit(s) from the filter * find hits from the
 * producer that start at the same position as a hit from the filter * find hits
 * from the producer that end at the same position as a hit from the filter
 */
public class SpanQueryPositionFilter extends BLSpanQueryAbstract {

    public static SpanGuarantees createGuarantees(SpanGuarantees producer) {
        return new SpanGuaranteesAdapter(producer) {
            @Override
            public boolean hitsStartPointSorted() {
                return true;
            }
        };
    }

    /** Filter operation to apply */
    final SpanQueryPositionFilter.Operation operation;

    /** Return producer spans that DON'T match the filter instead? */
    final boolean invert;

    /** How to adjust the leading edge of the producer hits while matching */
    int adjustLeading;

    /** How to adjust the trailing edge of the producer hits while matching */
    int adjustTrailing;

    /**
     * Produce hits that match filter hits.
     *
     * @param producer hits we may be interested in
     * @param filter how we determine what producer hits we're interested in
     * @param operation operation used to determine what producer hits we're interested in
     *            (containing, within, startsat, endsat)
     * @param invert produce hits that don't match filter instead?
     */
    public SpanQueryPositionFilter(BLSpanQuery producer, BLSpanQuery filter, SpanQueryPositionFilter.Operation operation,
            boolean invert) {
        this(producer, filter, operation, invert, 0, 0);
    }

    /**
     * Produce hits that match filter hits.
     *
     * @param producer hits we may be interested in
     * @param filter how we determine what producer hits we're interested in
     * @param operation operation used to determine what producer hits we're interested in
     *            (containing, within, startsat, endsat)
     * @param invert produce hits that don't match filter instead?
     * @param adjustLeading how to adjust the leading edge of the producer hits while
     *            matching
     * @param adjustTrailing how to adjust the trailing edge of the producer hits while
     *            matching
     */
    public SpanQueryPositionFilter(BLSpanQuery producer, BLSpanQuery filter, SpanQueryPositionFilter.Operation operation,
            boolean invert, int adjustLeading, int adjustTrailing) {
        super(producer, filter);
        this.operation = operation;
        this.invert = invert;
        this.adjustLeading = adjustLeading;
        this.adjustTrailing = adjustTrailing;
        this.guarantees = createGuarantees(producer.guarantees());
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery producer = clauses.get(0).rewrite(reader);
        BLSpanQuery filter = clauses.get(1).rewrite(reader);

        if (!invert && operation != SpanQueryPositionFilter.Operation.STARTS_AT
                && operation != SpanQueryPositionFilter.Operation.ENDS_AT &&
                producer instanceof SpanQueryAnyToken tp) {
            // We're filtering "all n-grams of length min-max".
            // Use the special optimized SpanQueryFilterNGrams.
            return new SpanQueryFilterNGrams(filter, operation, tp.guarantees().hitsLengthMin(),
                    tp.guarantees().hitsLengthMax(), adjustLeading, adjustTrailing);
        }

        if (producer != clauses.get(0) || filter != clauses.get(1)) {
            SpanQueryPositionFilter result = new SpanQueryPositionFilter(producer, filter, operation, invert);
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
        Map<Term, TermStates> contexts = scoreMode.needsScores() ? getTermStates(prodWeight, filterWeight) : null;
        return new SpanWeightPositionFilter(prodWeight, filterWeight, searcher, contexts, boost);
    }

    class SpanWeightPositionFilter extends BLSpanWeight {

        final BLSpanWeight prodWeight, filterWeight;

        public SpanWeightPositionFilter(BLSpanWeight prodWeight, BLSpanWeight filterWeight, IndexSearcher searcher,
                Map<Term, TermStates> terms, float boost) throws IOException {
            super(SpanQueryPositionFilter.this, searcher, terms, boost);
            this.prodWeight = prodWeight;
            this.filterWeight = filterWeight;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return prodWeight.isCacheable(ctx) && filterWeight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            prodWeight.extractTermStates(contexts);
            filterWeight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spansProd = prodWeight.getSpans(context, requiredPostings);
            if (spansProd == null)
                return null;
            BLSpans spansFilter = filterWeight.getSpans(context, requiredPostings);
            if (spansFilter == null) {
                // No filter hits. If it's a positive filter, that means no producer hits can match.
                // If it's a negative filter, all producer hits match.
                return invert ? spansProd : null;
            }
            return new SpansPositionFilter(spansProd, spansFilter, operation, invert, adjustLeading, adjustTrailing);
        }
    }

    /** The different positional operations */
    public enum Operation {

        /** Producer hit contains filter hit */
        CONTAINING,

        /** Producer hit contained in filter hit */
        WITHIN,

        /** Producer hit starts at filter hit */
        STARTS_AT,

        /** Producer hit ends at filter hit */
        ENDS_AT,

        /** Producer hit exactly matches filter hit */
        MATCHES,

        /** Producer hit contains filter hit, at its end */
        CONTAINING_AT_START,

        /** Producer hit contains filter hit, at its start */
        CONTAINING_AT_END;

        public static Operation fromStringValue(String s) {
            for (Operation op : values()) {
                if (op.name().equalsIgnoreCase(s)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("Unknown operation: " + s);
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    @Override
    public String toString(String field) {
        String not = invert ? "not" : "";
        String adj = (adjustLeading != 0 || adjustTrailing != 0 ? ", " + adjustLeading + ", " + adjustTrailing : "");
        return "POSFILTER(" + clausesToString(field) + ", " + not + operation + adj + ")";
    }

    public SpanQueryPositionFilter copy() {
        return new SpanQueryPositionFilter(clauses.get(0), clauses.get(1), operation, invert, adjustLeading, adjustTrailing);
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
        return new SpanQueryPositionFilter(clauses.get(0).noEmpty(), clauses.get(1), operation, invert, adjustLeading,
                adjustTrailing);
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
            throw new RuntimeException("Trying to internalize non-constant-length clause: " + clause);
        // Create a new position filter query with a constant-length clause added to our producer.
        // adjustLeading and adjustTrailing are updated according to the clause's length, so it is not
        // actually filtered.
        BLSpanQuery producer = clauses.get(0);
        SpanQuerySequence seq = SpanQuerySequence.sequenceInternalize(producer, clause, addToRight);
        if (addToRight)
            return new SpanQueryPositionFilter(seq, clauses.get(1), operation, invert, adjustLeading,
                    adjustTrailing - clause.guarantees().hitsLengthMin());
        return new SpanQueryPositionFilter(seq, clauses.get(1), operation, invert,
                adjustLeading + clause.guarantees().hitsLengthMin(), adjustTrailing);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (invert ? 1231 : 1237);
        result = prime * result + adjustLeading;
        result = prime * result + ((operation == null) ? 0 : operation.hashCode());
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
        SpanQueryPositionFilter other = (SpanQueryPositionFilter) obj;
        if (invert != other.invert)
            return false;
        if (adjustLeading != other.adjustLeading)
            return false;
        if (operation != other.operation)
            return false;
        if (adjustTrailing != other.adjustTrailing)
            return false;
        return true;
    }
}
