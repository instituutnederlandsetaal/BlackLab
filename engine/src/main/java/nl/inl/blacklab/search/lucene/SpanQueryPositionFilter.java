package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;

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
    final SpanFilter operation;

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
    public SpanQueryPositionFilter(BLSpanQuery producer, BLSpanQuery filter, SpanFilter operation,
            boolean invert) {
        this(producer, filter, operation, invert, 0, 0);
    }

    /**
     * Produce hits that match filter hits.
     *
     * Note that the two adjustments only apply (to the producer hits) while matching.
     * If a match is found, the original (unadjusted) producer hit is produced.
     *
     * This allows us to easily internalize fixed-length neighbouring clauses into the producer
     * clause.
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
    public SpanQueryPositionFilter(BLSpanQuery producer, BLSpanQuery filter, SpanFilter operation,
            boolean invert, int adjustLeading, int adjustTrailing) {
        super(producer, filter);
        this.operation = operation;
        this.invert = invert;
        this.adjustLeading = adjustLeading;
        this.adjustTrailing = adjustTrailing;
        this.guarantees = createGuarantees(producer.guarantees());
    }

    /** Which of these "identical" clauses should we keep?
     *
     * This exists because implicit captures may be optimized away, but we want to select the one without the
     * tie-breaking number attached (s, not s2)
     */
    static BLSpanQuery chooseBetweenEqual(BLSpanQuery a, BLSpanQuery b) {
        if (a instanceof SpanQueryRelations r && b instanceof SpanQueryRelations s && r.isImplicitCapture()) {
            // Identical clauses with implicit (auto-generated) captures, e.g. <s/> captured as s and s2.
            // Select the one with the shortest capture name so we keep the s capture, not the s2 one.
            // Relevant for e.g. (X within <s/>) within </s> optimization.
            return r.getCaptureAs().length() < s.getCaptureAs().length() ? r : s;
        }
        return a;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery producer = getProducer().rewrite(reader);
        BLSpanQuery filter = getFilter().rewrite(reader);

        if (producer.equals(filter)) {
            // Identical clauses; trivial case
            if (invert) {
                // e.g. <s/> not within <s/>
                return new SpanQueryNoHits(queryInfo, luceneFieldName);
            }
            // e.g. <s/> within <s/> --> <s/>
            // (this works for all operations)
            return chooseBetweenEqual(producer, filter);
        }

        // Can we simplify a trivial nested position filter?
        // (same results, much better performance)
        switch (operation) {
            case WITHIN -> {
                if (producer instanceof SpanQueryPositionFilter pf && !pf.invert) {
                    if (pf.operation == SpanFilter.CONTAINING && pf.getProducer().equals(filter)) {
                        // (L containing S) within L --> L containing S
                        BLSpanQuery newProducer = chooseBetweenEqual(pf.getProducer(), filter);
                        return newProducer == pf.getProducer() ? pf :
                                new SpanQueryPositionFilter(newProducer, pf.getFilter(), SpanFilter.CONTAINING, false);
                    } else if (pf.operation == SpanFilter.WITHIN && pf.getFilter().equals(filter)) {
                        // (S within L) within L --> S within L
                        BLSpanQuery newFilter = chooseBetweenEqual(pf.getFilter(), filter);
                        return newFilter == pf.getFilter() ? pf :
                                new SpanQueryPositionFilter(pf.getProducer(), newFilter, SpanFilter.WITHIN, false);
                    }
                } else if (filter instanceof SpanQueryPositionFilter pf && !pf.invert) {
                    if (pf.operation == SpanFilter.CONTAINING && pf.getFilter().equals(producer)) {
                        // S within (L containing S) --> S within L
                        BLSpanQuery newProducer = chooseBetweenEqual(pf.getFilter(), producer);
                        return new SpanQueryPositionFilter(newProducer, pf.getProducer(), SpanFilter.WITHIN, false);
                    } else if (pf.operation == SpanFilter.WITHIN && pf.getProducer().equals(producer)) {
                        // S within (S within L) --> S within L
                        BLSpanQuery newProducer = chooseBetweenEqual(pf.getProducer(), producer);
                        return newProducer == pf.getProducer() ? pf :
                                new SpanQueryPositionFilter(newProducer, pf.getFilter(), SpanFilter.WITHIN, false);
                    }
                }
            }
            case CONTAINING -> {
                if (producer instanceof SpanQueryPositionFilter pf && !pf.invert) {
                    if (pf.operation == SpanFilter.CONTAINING && pf.getFilter().equals(filter)) {
                        // (L containing S) containing S --> L containing S
                        BLSpanQuery newFilter = chooseBetweenEqual(pf.getFilter(), filter);
                        return newFilter == pf.getFilter() ? pf :
                                new SpanQueryPositionFilter(pf.getProducer(), newFilter, SpanFilter.CONTAINING, false);
                    } else if (pf.operation == SpanFilter.WITHIN && pf.getProducer().equals(filter)) {
                        // (S within L) containing S => S within L
                        BLSpanQuery newProducer = chooseBetweenEqual(pf.getProducer(), filter);
                        return newProducer == pf.getProducer() ? pf :
                                new SpanQueryPositionFilter(newProducer, pf.getFilter(), SpanFilter.WITHIN, false);
                    }
                } else if (filter instanceof SpanQueryPositionFilter pf && !pf.invert) {
                    if (pf.operation == SpanFilter.CONTAINING && pf.getProducer().equals(producer)) {
                        // L containing (L containing S) --> L containing S
                        BLSpanQuery newProducer = chooseBetweenEqual(pf.getProducer(), producer);
                        return newProducer == pf.getProducer() ? pf :
                                new SpanQueryPositionFilter(newProducer, pf.getFilter(), SpanFilter.CONTAINING, false);
                    } else if (pf.operation == SpanFilter.WITHIN && pf.getFilter().equals(producer)) {
                        // L containing (S within L) --> L containing S
                        BLSpanQuery newProducer = chooseBetweenEqual(pf.getFilter(), producer);
                        return new SpanQueryPositionFilter(newProducer, pf.getProducer(), SpanFilter.CONTAINING, false);
                    }
                }
            }
        }

        if (!invert && operation != SpanFilter.STARTS_AT
                && operation != SpanFilter.ENDS_AT &&
                producer instanceof SpanQueryAnyToken tp) {
            // We're filtering "all n-grams of length min-max".
            // Use the special optimized SpanQueryFilterNGrams.
            return new SpanQueryFilterNGrams(filter, operation, tp.guarantees().hitsLengthMin(),
                    tp.guarantees().hitsLengthMax(), adjustLeading, adjustTrailing);
        }

        if (producer != clauses.get(0) || filter != clauses.get(1)) {
            // One of the clauses was rewritten, so we need to create a new SpanQueryPositionFilter.
            SpanQueryPositionFilter result = new SpanQueryPositionFilter(producer, filter, operation, invert);
            result.adjustLeading = adjustLeading;
            result.adjustTrailing = adjustTrailing;
            return result;
        }
        return this;
    }

    public BLSpanQuery getProducer() {
        return clauses.get(0);
    }

    public BLSpanQuery getFilter() {
        return clauses.get(1);
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
            throw new IllegalStateException("Trying to internalize non-constant-length clause: " + clause);
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
