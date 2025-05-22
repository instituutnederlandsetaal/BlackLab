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
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.util.LuceneUtil;

/**
 * A SpanQuery matching a number of tokens without any restrictions.
 */
public class SpanQueryAnyToken extends BLSpanQuery {

    public static SpanGuarantees createGuarantees(int min, int max) {
        return new SpanGuaranteesAdapter() {
            @Override
            public boolean okayToInvertForOptimization() {
                // Yes, inverting is actually an improvement
                return isSingleAnyToken();
            }

            @Override
            public boolean hitsAllSameLength() {
                return min == max;
            }

            @Override
            public int hitsLengthMin() {
                return min;
            }

            @Override
            public int hitsLengthMax() {
                return max;
            }

            @Override
            public boolean hitsEndPointSorted() {
                return hitsAllSameLength();
            }

            @Override
            public boolean hitsStartPointSorted() {
                return true;
            }

            @Override
            public boolean hitsHaveUniqueStart() {
                return min == max;
            }

            @Override
            public boolean hitsHaveUniqueEnd() {
                return min == max;
            }

            @Override
            public boolean hitsHaveUniqueStartEnd() {
                return true;
            }

            @Override
            public boolean isSingleAnyToken() {
                return min == 1 && max == 1;
            }
        };
    }

    /** The minimum number of tokens in this stretch. */
    protected final int min;

    /** The maximum number of tokens in this stretch. */
    protected final int max;

    final String luceneField;

    public SpanQueryAnyToken(QueryInfo queryInfo, int min, int max, String luceneField) {
        super(queryInfo);
        this.min = min;
        this.max = max;
        this.luceneField = luceneField;
        this.guarantees = createGuarantees(min, max);
    }

    /**
     * A query matching any n-gram ([]*).
     *
     * Mostly useful as a "don't care" value that will never be executed.
     * Actually executing this query will generate a very large number of hits.
     *
     * @param queryInfo query info
     * @param context query execution context
     * @return the query
     */
    public static SpanQueryAnyToken anyNGram(QueryInfo queryInfo, String luceneField) {
        return new SpanQueryAnyToken(queryInfo, 0, MAX_UNLIMITED, luceneField);
    }

    @Override
    public boolean matchesEmptySequence() {
        return min == 0;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (min > 0)
            return this;
        return new SpanQueryAnyToken(queryInfo, 1, max, luceneField);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((luceneField == null) ? 0 : luceneField.hashCode());
        result = prime * result + max;
        result = prime * result + min;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SpanQueryAnyToken other = (SpanQueryAnyToken) obj;
        if (luceneField == null) {
            if (other.luceneField != null)
                return false;
        } else if (!luceneField.equals(other.luceneField))
            return false;
        if (max != other.max)
            return false;
        if (min != other.min)
            return false;
        return true;
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField())) {
            visitor.visitLeaf(this);
        }
    }

    @Override
    public BLSpanWeight createWeight(final IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        final int realMin = min == 0 ? 1 : min; // always rewritten unless the whole query is optional
        return new BLSpanWeight(this, searcher, null, boost) {

            @Override
            public boolean isCacheable(LeafReaderContext ctx) {
                return true;
            }

            @Override
            public void extractTermStates(Map<Term, TermStates> contexts) {
                // No terms
            }

            @Override
            public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) {
                return new SpansNGrams(context.reader(), getField(), realMin, max);
            }
        };
    }

    @Override
    public String toString(String field) {
        return "ANYTOKEN(" + min + ", " + inf(max) + ")";
    }

    @Override
    public String getRealField() {
        return luceneField;
    }

    @Override
    public BLSpanQuery inverted() {
        return new SpanQueryNoHits(queryInfo, luceneField);
    }

    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        final int realMin = min == 0 ? 1 : min; // always rewritten unless the whole query is optional
        NfaState state = NfaState.anyToken(luceneField, null);
        Nfa frag = new Nfa(state, List.of(state));
        if (realMin != 1 || max != 1) {
            frag.repeat(realMin, max);
        }
        return frag;
    }

    @Override
    public boolean canMakeNfa() {
        return true;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        // Should be rewritten, and if not, it matches all positions in the index.
        int numberOfExpansionSteps = max < 0 ? 50 : max - min + 1;
        return LuceneUtil.getSumTotalTermFreq(reader, luceneField) * numberOfExpansionSteps;
    }

    @Override
    public int forwardMatchingCost() {
        int cost = 0;
        int nMax = max == MAX_UNLIMITED ? 50 : max;
        for (int i = min; i <= nMax; i++) {
            cost += i;
        }
        return cost;
    }

    public BLSpanQuery addRep(int addMin, int addMax) {
        int nMin = min + addMin;
        int nMax = BLSpanQuery.addMaxValues(max, addMax);
        return new SpanQueryAnyToken(queryInfo, nMin, nMax, luceneField);
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) {
        return this;
    }

}
