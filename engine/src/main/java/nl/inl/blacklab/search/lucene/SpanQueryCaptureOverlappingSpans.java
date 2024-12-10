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
 * Captures all matching spans enclosing the current hit.
 */
public class SpanQueryCaptureOverlappingSpans extends BLSpanQueryAbstract {

    String captureAs;

    public SpanQueryCaptureOverlappingSpans(BLSpanQuery query, BLSpanQuery spans, String captureAs) {
        super(query, spans);
        this.captureAs = captureAs;
        this.guarantees = query.guarantees();
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        return rewritten == null ? this :
                new SpanQueryCaptureOverlappingSpans(rewritten.get(0), rewritten.get(1), captureAs);
    }

    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence();
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        return new SpanQueryCaptureOverlappingSpans(clauses.get(0).noEmpty(), clauses.get(1).noEmpty(),
                captureAs);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight queryWeight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        BLSpanWeight spansWeight = clauses.get(1).createWeight(searcher, scoreMode, boost);
        return new Weight(queryWeight, spansWeight, searcher, scoreMode.needsScores() ? getTermStates(queryWeight) : null, boost);
    }

    class Weight extends BLSpanWeight {

        final BLSpanWeight queryWeight;

        private final BLSpanWeight spansWeight;

        public Weight(BLSpanWeight queryWeight, BLSpanWeight spansWeight, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryCaptureOverlappingSpans.this, searcher, terms, boost);
            this.queryWeight = queryWeight;
            this.spansWeight = spansWeight;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            queryWeight.extractTerms(terms);
            spansWeight.extractTerms(terms);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return queryWeight.isCacheable(ctx) && spansWeight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            queryWeight.extractTermStates(contexts);
            spansWeight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans queryHits = queryWeight.getSpans(context, requiredPostings);
            if (queryHits == null)
                return null;
            BLSpans spanHits = spansWeight.getSpans(context, requiredPostings);
            if (spanHits == null) {
                // This can happen if these relations don't occur in this segment of the index
                return queryHits;
            }
            return new SpansCaptureOverlappingSpans(BLSpans.ensureSorted(queryHits),
                    BLSpans.ensureSorted(spanHits), captureAs);
        }
    }

    @Override
    public String toString(String field) {
        return "within-spans(" + clausesToString(field) + ", " + captureAs + ")";
    }

    @Override
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        // could work if clause is fixed-length, but we need to take that length into account when capturing spans
        return false;
    }

    @Override
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        // (see above)
        return new SpanQueryCaptureOverlappingSpans(
                SpanQuerySequence.sequenceInternalize(clauses.get(0), clause, onTheRight),
                clauses.get(1), captureAs);
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clauses.get(0).reverseMatchingCost(reader) + clauses.get(1).reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.get(0).forwardMatchingCost() + clauses.get(1).forwardMatchingCost();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }
}
