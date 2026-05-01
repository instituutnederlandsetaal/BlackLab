package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.jspecify.annotations.NonNull;

/**
 * Returns either the leading edge or trailing edge of the specified query.
 *
 * E.g. for left-to-right languages, the leading edge is the left edge
 * and the trailing edge is the right edge.
 *
 * Note that the results of this query are zero-length spans.
 */
public class SpanQueryEdge extends BLSpanQueryAbstract {

    public static SpanGuarantees createGuarantees(SpanGuarantees clause, boolean trailingEdge) {
        return new SpanGuaranteesAdapter() {
            @Override
            public boolean hitsAllSameLength() {
                return true;
            }

            @Override
            public int hitsLengthMin() {
                return 0;
            }

            @Override
            public int hitsLengthMax() {
                return 0;
            }

            @Override
            public boolean hitsStartPointSorted() {
                return trailingEdge ? clause.hitsEndPointSorted() : clause.hitsStartPointSorted();
            }

            @Override
            public boolean hitsEndPointSorted() {
                return hitsStartPointSorted();
            }

            @Override
            public boolean hitsHaveUniqueStart() {
                return trailingEdge ? clause.hitsHaveUniqueEnd() : clause.hitsHaveUniqueStart();
            }

            @Override
            public boolean hitsHaveUniqueEnd() {
                return hitsHaveUniqueStart();
            }

            @Override
            public boolean hitsHaveUniqueStartEnd() {
                return hitsHaveUniqueStart();
            }
        };
    }

    /** if true, return the trailing edges; if false, the leading ones */
    final boolean trailingEdge;

    /**
     * Construct SpanQueryEdge object.
     * 
     * @param query the query to determine edges from
     * @param trailingEdge if true, return the trailing edges; if false, the leading ones
     */
    public SpanQueryEdge(BLSpanQuery query, boolean trailingEdge) {
        super(query);
        this.trailingEdge = trailingEdge;
        this.guarantees = createGuarantees(query.guarantees(), trailingEdge);
    }

    public static @NonNull BLSpanQuery lookAheadBehindQuery(BLSpanQuery clause, boolean behind, boolean negate) {
        BLSpanQuery result = new SpanQueryEdge(clause, behind);
        if (negate) {
            // Expand edges to single tokens (in the correct direction)
            int startAdjust = behind ? -1 : 0;
            int endAdjust = behind ? 0 : 1;
            SpanQueryAdjustHits singleTokens = new SpanQueryAdjustHits(result, startAdjust, endAdjust);
            // Get all non-matching tokens instead, then go back to only the edges
            result = new SpanQueryEdge(new SpanQueryNot(singleTokens), behind);
        }
        return result;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        return rewritten == null ? this : new SpanQueryEdge(rewritten.get(0), trailingEdge);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight weight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        return new SpanWeightEdge(weight, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
    }

    class SpanWeightEdge extends BLSpanWeight {

        final BLSpanWeight weight;

        public SpanWeightEdge(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryEdge.this, searcher, terms, boost);
            this.weight = weight;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return weight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            weight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {

            BLSpans spans = weight.getSpans(context, requiredPostings);
            if (spans == null)
                return null;
            return new SpansEdge(spans, trailingEdge);
        }
    }

    @Override
    public String toString(String field) {
        return "EDGE(" + clausesToString(field) + ", " + (trailingEdge ? "R" : "L") + ")";
    }

    public boolean isTrailingEdge() {
        return trailingEdge;
    }

    public String getElementNameRegex() {
        BLSpanQuery cl = clauses.get(0);
        if (cl instanceof SpanQueryRelations) {
            return ((SpanQueryRelations) cl).getElementNameRegex();
        }
        return null;
    }

    public BLSpanQuery getClause() {
        return clauses.get(0);
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
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean addAtEnd) {
        boolean atLeastOneConstantLength = guarantees().hitsAllSameLength() || clause.guarantees().hitsAllSameLength();
        return atLeastOneConstantLength && isTrailingEdge() == addAtEnd;
    }

    @Override
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clauseToInternalize, boolean addAtEnd) {
        if (!canInternalizeNeighbour(clauseToInternalize, addAtEnd))
            throw new IllegalStateException("Trying to internalize, but there's no constant-length clause");

        SpanQuerySequence internalizedSequence = SpanQuerySequence.sequenceInternalize(getClause(),
                clauseToInternalize, addAtEnd);
        if (clauseToInternalize.guarantees().hitsAllSameLength()) {
            // We're trying to internalize a fixed-length clause.
            if (isTrailingEdge()) {
                // We need the trailing edge of our clause.
                if (addAtEnd) {
                    // Internalize the fixed-length clause to the leading side, adjusting the trailing edge of the result hit
                    // e.g. (?<= "the" []{1,3} ) "dog"  --> _adjust(_edge("the" []{1,3} "dog", "trailing"), -1, 0)
                    return new SpanQueryAdjustHits(new SpanQueryEdge(internalizedSequence, true), -1, 0);
                } else {
                    // Internalize the fixed-length clause by ANDing it to the trailing side of our clause.
                    // e.g. "dog" (?<= "the" []{1,3} )  --> _adjust(_edge("the" []{0,2} "dog", "trailing"), -1, 0)
                    // (this case may be too complex as we have to "and" sequences of different lengths together)
                    throw new UnsupportedOperationException("TOO COMPLEX");
                }
            } else {
                // We need the leading edge of our clause.
                if (addAtEnd) {
                    // Internalize the fixed-length clause by ANDing it to the leading side of our clause.
                    // e.g. (?= "the" []{1,3} ) "dog"  --> _adjust(_edge([word="the" & word="dog"] []{1,3}, "leading"), 0, 1)
                    // (this case may be too complex as we have to "and" sequences of different lengths together)
                    throw new UnsupportedOperationException("TOO COMPLEX");
                } else {
                    // Internalize the fixed-length clause to the leading side, adjusting the leading edge of the result hit
                    // e.g. "dog" (?= "the" []{1,3} )  --> _adjust(_edge("dog" "the" []{1,3}, "leading"), 0, 1)
                    return new SpanQueryAdjustHits(new SpanQueryEdge(internalizedSequence, false), 0, 1);
                }
            }
        } else {
            // Our clause is fixed-length, the clause we're internalizing is not.
            if (isTrailingEdge()) {
                // We need the trailing edge of our clause.
                if (addAtEnd) {
                    // Internalize the clause to the trailing side, adjusting the result hit
                    // e.g. (?<= "the" ) []{1,3} "dog"  --> _adjust("the" []{1,3} "dog", 1, 0)
                    return new SpanQueryAdjustHits(internalizedSequence, 1, 0);
                } else {
                    // Internalize the clause by ANDing it to the trailing side of our clause.
                    // e.g. "dog" []{1,3} (?<= "the" )  --> _adjust("dog" []{0, 2} "the", 0, -1)
                    // (this case may be too complex as we have to "and" sequences of different lengths together)
                    throw new UnsupportedOperationException("TOO COMPLEX");
                }
            } else {
                // We need the leading edge of our clause.
                if (addAtEnd) {
                    // Internalize the clause by ANDing it to the leading side of our clause.
                    // e.g. (?= "the" ) []{1,3} "dog"  --> "the" []{0,2} "dog"
                    // (this case may be too complex as we have to "and" sequences of different lengths together)
                    throw new UnsupportedOperationException("TOO COMPLEX");
                } else {
                    // Internalize the clause to the leading side, adjusting the result hit
                    // e.g. "dog" []{1,3} (?= "the" )  --> _adjust("dog" []{1,3} "the", 0, -1)
                    return new SpanQueryAdjustHits(internalizedSequence, 0, -1);
                }
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (trailingEdge ? 1231 : 1237);
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
        SpanQueryEdge other = (SpanQueryEdge) obj;
        return trailingEdge == other.trailingEdge;
    }
}
