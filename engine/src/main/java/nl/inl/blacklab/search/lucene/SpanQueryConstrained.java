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

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.matchfilter.MatchFilter;

/**
 * Apply a global constraint (or "match filter") to our matches.
 *
 * A global constraint is specified in Corpus Query Language using
 * the :: operator, e.g. <code>a:[] "and" b:[] :: a.word = b.word</code>
 * to find things like "more and more", "less and less", etc.
 */
public class SpanQueryConstrained extends BLSpanQueryAbstract {

    final MatchFilter constraint;

    final ForwardIndexAccessor fiAccessor;

    public SpanQueryConstrained(BLSpanQuery clause, MatchFilter constraint, ForwardIndexAccessor fiAccessor) {
        super(clause);
        this.constraint = constraint;
        this.fiAccessor = fiAccessor;
        this.guarantees = clause.guarantees();
    }

    @Override
    public String toString(String field) {
        return "CONSTRAINT(" + clauses.get(0) + ", " + constraint + ")";
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> cl = rewriteClauses(reader);
        MatchFilter rewrittenConstraint = constraint.rewrite();
        if (cl == null && rewrittenConstraint == constraint)
            return this;
        if (cl == null)
            cl = clauses;
        return new SpanQueryConstrained(cl.get(0), rewrittenConstraint, fiAccessor);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight prodWeight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        Map<Term, TermStates> contexts = scoreMode.needsScores() ? getTermStates(prodWeight) : null;
        constraint.lookupAnnotationIndices(fiAccessor);
        return new SpanWeightConstrained(prodWeight, constraint, searcher, contexts, boost);
    }

    class SpanWeightConstrained extends BLSpanWeight {

        final BLSpanWeight prodWeight;

        private final MatchFilter constraint;

        public SpanWeightConstrained(BLSpanWeight prodWeight, MatchFilter constraint, IndexSearcher searcher,
                Map<Term, TermStates> contexts, float boost) throws IOException {
            super(SpanQueryConstrained.this, searcher, contexts, boost);
            this.prodWeight = prodWeight;
            this.constraint = constraint;
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
            return new SpansConstrained(spansProd, constraint,
                    fiAccessor.getForwardIndexAccessorLeafReader(context));
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
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((constraint == null) ? 0 : constraint.hashCode());
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
        SpanQueryConstrained other = (SpanQueryConstrained) obj;
        if (constraint == null) {
            if (other.constraint != null)
                return false;
        } else if (!constraint.equals(other.constraint))
            return false;
        return true;
    }

}
