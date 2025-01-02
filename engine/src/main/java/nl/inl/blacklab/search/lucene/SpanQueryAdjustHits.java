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
 * Adjust the start and end of hits while matching.
 */
public class SpanQueryAdjustHits extends BLSpanQueryAbstract {

    /** How to adjust the starts of hits. */
    private final int startAdjust;

    /** How to adjust the ends of hits. */
    private final int endAdjust;

    /**
     * Construct SpanQueryAdjustHits object.
     *
     * @param query the query to adjust hits from
     * @param startAdjust how to adjust start positions
     * @param endAdjust how to adjust end positions
     */
    public SpanQueryAdjustHits(BLSpanQuery query, int startAdjust, int endAdjust) {
        super(query);
        this.startAdjust = startAdjust;
        this.endAdjust = endAdjust;
        this.guarantees = query.guarantees();
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        if (startAdjust == 0 && endAdjust == 0)
            return clauses.get(0).rewrite(reader);
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        return rewritten == null ? this : new SpanQueryAdjustHits(rewritten.get(0), startAdjust, endAdjust);
    }

    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence();
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        return new SpanQueryAdjustHits(clauses.get(0).noEmpty(), startAdjust, endAdjust);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight weight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        return new SpanWeightAdjustHits(weight, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
    }

    public BLSpanQuery getClause() {
        return clauses.get(0);
    }

    public BLSpanQuery copyWith(BLSpanQuery query) {
        return new SpanQueryAdjustHits(query, startAdjust, endAdjust);
    }

    class SpanWeightAdjustHits extends BLSpanWeight {

        final BLSpanWeight weight;

        public SpanWeightAdjustHits(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryAdjustHits.this, searcher, terms, boost);
            this.weight = weight;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            weight.extractTerms(terms);
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
            return new SpansAdjustHits(context.reader(), field, spans, startAdjust, endAdjust);
        }
    }

    @Override
    public String toString(String field) {
        String adj = (startAdjust != 0 || endAdjust != 0 ? ", " + startAdjust + ", " + endAdjust : "");
        return "ADJUST(" + clausesToString(field) + adj + ")";
    }

    @Override
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean atClauseEnd) {
        // Because our clause hits need to be adjusted, we can't easily internalize neighbours
        // (OPT: we'd need a SpanQuerySequence where consecutive clauses don't all have to match their ends and starts,
        //  but are allowed to overlap or have gaps. Possible future optimization?)
        return startAdjust == 0 && endAdjust == 0;
    }

    @Override
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clause, boolean atClauseEnd) {
        if (!clause.guarantees().hitsAllSameLength())
            throw new IllegalArgumentException("Can only internalize fixed-length clause!");
        // Check how to adjust the capture group edges after internalization
        int newStartAdjust = startAdjust, newEndAdjust = endAdjust;
        int clauseLength = clause.guarantees().hitsLengthMin();
        if (atClauseEnd)
            newEndAdjust -= clauseLength;
        else
            newStartAdjust += clauseLength;
        return new SpanQueryAdjustHits(SpanQuerySequence.sequenceInternalize(clauses.get(0), clause, atClauseEnd),
                newStartAdjust, newEndAdjust);
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
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        SpanQueryAdjustHits that = (SpanQueryAdjustHits) o;
        return startAdjust == that.startAdjust && endAdjust == that.endAdjust;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), startAdjust, endAdjust);
    }
}
