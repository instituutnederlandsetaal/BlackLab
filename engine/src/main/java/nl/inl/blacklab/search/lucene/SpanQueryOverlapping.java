package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

public class SpanQueryOverlapping extends BLSpanQueryAbstract {

    /** Filter operation to apply */
    final SpansOverlapping.Operation operation;

    /**
     * Produce hits that match right hits.
     *
     * @param left left clause
     * @param right right clause
     * @param operation how to determine hits from the two clauses (i.e. find overlap)
     */
    public SpanQueryOverlapping(BLSpanQuery left, BLSpanQuery right, String operation) {
        this(left, right, SpansOverlapping.Operation.fromStringValue(operation));
    }

    /**
     * Produce hits that match right hits.
     *
     * @param left left clause
     * @param right right clause
     * @param operation how to determine hits from the two clauses (i.e. find overlap)
     */
    public SpanQueryOverlapping(BLSpanQuery left, BLSpanQuery right, SpansOverlapping.Operation operation) {
        super(left, right);
        this.operation = operation;
        this.guarantees = this.operation.createGuarantees(left.guarantees(), right.guarantees());
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery left = clauses.get(0).rewrite(reader);
        BLSpanQuery right = clauses.get(1).rewrite(reader);
        if (left != clauses.get(0) || right != clauses.get(1)) {
            return new SpanQueryOverlapping(left, right, operation);
        }
        return this;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight left = clauses.get(0).createWeight(searcher, scoreMode, boost);
        BLSpanWeight right = clauses.get(1).createWeight(searcher, scoreMode, boost);
        Map<Term, TermStates> contexts = scoreMode.needsScores() ? getTermStates(left, right) : null;
        return new SpanWeightOverlapping(left, right, operation, searcher, contexts, boost);
    }

    class SpanWeightOverlapping extends BLSpanWeight {

        private final BLSpanWeight left, right;

        private final SpansOverlapping.Operation operation;

        public SpanWeightOverlapping(BLSpanWeight left, BLSpanWeight right, SpansOverlapping.Operation operation,
                IndexSearcher searcher,
                Map<Term, TermStates> terms, float boost) throws IOException {
            super(SpanQueryOverlapping.this, searcher, terms, boost);
            this.left = left;
            this.right = right;
            this.operation = operation;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return left.isCacheable(ctx) && right.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            left.extractTermStates(contexts);
            right.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spansProd = left.getSpans(context, requiredPostings);
            if (spansProd == null)
                return null;
            BLSpans spansFilter = right.getSpans(context, requiredPostings);
            if (spansFilter == null)
                return null;
            return new SpansOverlapping(spansProd, spansFilter, operation);
        }
    }

    @Override
    public String toString(String field) {
        return "OVERLAPPING(" + clausesToString(field) + ", " + operation + ")";
    }

    public SpanQueryOverlapping copy() {
        return new SpanQueryOverlapping(clauses.get(0), clauses.get(1), operation);
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clauses.get(0).reverseMatchingCost(reader) + clauses.get(1).reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.get(0).forwardMatchingCost() +
                clauses.get(1).forwardMatchingCost();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        SpanQueryOverlapping that = (SpanQueryOverlapping) o;
        return Objects.equals(operation, that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), operation);
    }
}
