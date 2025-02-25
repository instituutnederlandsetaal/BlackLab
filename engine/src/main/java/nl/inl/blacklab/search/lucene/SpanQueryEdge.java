package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

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

    @Deprecated
    public boolean isRightEdge() {
        return isTrailingEdge();
    }

    public boolean isTrailingEdge() {
        return trailingEdge;
    }

    public String getElementNameRegex() {
        BLSpanQuery cl = clauses.get(0);
        if (cl instanceof TagQuery) {
            return ((TagQuery) cl).getElementNameRegex();
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
