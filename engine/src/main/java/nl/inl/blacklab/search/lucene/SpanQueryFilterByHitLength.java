package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

/**
 * Filter hits by min/max length.
 *
 * More efficient than using AND with an any token repetition clause.
 */
public class SpanQueryFilterByHitLength extends BLSpanQueryAbstract {

    final int min;

    final int max;

    public static SpanGuarantees createGuarantees(SpanGuarantees clause, int min, int max) {
        return new SpanGuaranteesAdapter(clause) {
            @Override
            public int hitsLengthMin() {
                return Math.max(super.hitsLengthMin(), min);
            }

            @Override
            public int hitsLengthMax() {
                return Math.min(super.hitsLengthMax(), max);
            }

            @Override
            public boolean hitsAllSameLength() {
                return super.hitsAllSameLength() || min == max;
            }
        };
    }

    public SpanQueryFilterByHitLength(BLSpanQuery clause, int min, int max) {
        super(clause);
        this.min = min;
        this.max = max;
        this.guarantees = clause.guarantees(); // TODO: add any extra guarantees from min/max
    }

    @Override
    public String toString(String field) {
        return "LENFILTER(" + clauses.get(0) + ", " + min + ", " + inf(max) + ")";
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> cl = rewriteClauses(reader);
        if (cl == null)
            cl = clauses;
        return new SpanQueryFilterByHitLength(cl.get(0), min, max);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight prodWeight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        Map<Term, TermStates> contexts = scoreMode.needsScores() ? getTermStates(prodWeight) : null;
        return new SpanWeightFilterByHitLength(prodWeight, searcher, contexts, boost);
    }

    class SpanWeightFilterByHitLength extends BLSpanWeight {

        final BLSpanWeight prodWeight;

        public SpanWeightFilterByHitLength(BLSpanWeight prodWeight, IndexSearcher searcher,
                Map<Term, TermStates> contexts, float boost) throws IOException {
            super(SpanQueryFilterByHitLength.this, searcher, contexts, boost);
            this.prodWeight = prodWeight;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return prodWeight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            prodWeight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spansProd = prodWeight.getSpans(context, requiredPostings);
            if (spansProd == null)
                return null;
            return new SpansFilterByHitLength(spansProd, min, max);
        }
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
    public String getRealField() {
        return clauses.get(0).getRealField();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        SpanQueryFilterByHitLength that = (SpanQueryFilterByHitLength) o;
        return min == that.min && max == that.max;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), min, max);
    }
}
