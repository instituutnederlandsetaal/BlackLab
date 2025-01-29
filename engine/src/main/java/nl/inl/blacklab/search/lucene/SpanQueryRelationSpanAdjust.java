package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Adjust relation spans to match source, target, or entire relation.
 */
public class SpanQueryRelationSpanAdjust extends BLSpanQuery {

    public static SpanGuarantees createGuarantees(SpanGuarantees clause, RelationInfo.SpanMode mode) {
        // NOTE: we don't know the direction here, so choose the one that gives the fewest guarantees
        //   (maybe SpanGuarantees could have a guarantee to help with this? Or maybe not worth it just for relations)
        SpanQueryRelations.Direction direction = SpanQueryRelations.Direction.BOTH_DIRECTIONS;
        return SpanQueryRelations.createGuarantees(clause, direction, mode);
    }

    private final BLSpanQuery clause;

    private final RelationInfo.SpanMode mode;

    private final String overriddenField;

    public SpanQueryRelationSpanAdjust(BLSpanQuery clause, RelationInfo.SpanMode mode, String overriddenField) {
        super(clause.queryInfo);
        this.clause = clause;
        this.mode = mode;

        this.guarantees = createGuarantees(clause.guarantees(), mode);
        this.overriddenField = overriddenField;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clause.rewrite(reader);
        if (rewritten instanceof SpanQueryRelations) {
            // We're just changing the span mode of a SpanQueryRelations;
            // more efficient to get the right span mode in the first place.
            RelationInfo.SpanMode m = mode == RelationInfo.SpanMode.ALL_SPANS ? RelationInfo.SpanMode.FULL_SPAN : mode;
            return ((SpanQueryRelations) rewritten).withSpanMode(m);
        } else if (rewritten instanceof SpanQueryRelationSpanAdjust) {
            // We're just changing the span mode of a SpanQueryRelationSpanAdjust;
            // more efficient to get the right span mode in the first place.
            return ((SpanQueryRelationSpanAdjust) rewritten).withSpanMode(mode);
        }
        if (rewritten == clause)
            return this;
        return new SpanQueryRelationSpanAdjust(rewritten, mode, overriddenField);
    }

    private BLSpanQuery withSpanMode(RelationInfo.SpanMode mode) {
        if (this.mode == mode)
            return this;
        return new SpanQueryRelationSpanAdjust(clause, mode, overriddenField);
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField())) {
            clause.visit(visitor.getSubVisitor(Occur.MUST, this));
        }
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight weight = clause.createWeight(searcher, scoreMode, boost);
        return new Weight(weight, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
    }

    class Weight extends BLSpanWeight {

        final BLSpanWeight weight;

        public Weight(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryRelationSpanAdjust.this, searcher, terms, boost);
            this.weight = weight;
        }

        @Override
        @Deprecated
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
            spans = new SpansRelationSpanAdjust(spans, mode, clause.getField());
            if (overriddenField != null)
                spans = new SpansOverrideField(spans, overriddenField);
            return spans;
        }
    }

    @Override
    public String toString(String field) {
        return "RSPAN(" + clause + ", " + mode + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        SpanQueryRelationSpanAdjust that = (SpanQueryRelationSpanAdjust) o;
        return Objects.equals(clause, that.clause) && mode == that.mode && Objects.equals(
                overriddenField, that.overriddenField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clause, mode, overriddenField);
    }

    /**
     * Returns the name of the search field. In the case of a annotated field, the
     * clauses may actually query different annotations of the same annotated field
     * (e.g. "description" and "description__pos"). That's why only the prefix is
     * returned.
     *
     * @return name of the search field
     */
    @Override
    public String getField() {
        if (overriddenField != null)
            return AnnotatedFieldNameUtil.getBaseName(overriddenField);
        return clause.getField();
    }

    @Override
    public String getRealField() {
        if (overriddenField != null)
            return overriddenField;
        return clause.getRealField();
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clause.reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clause.forwardMatchingCost();
    }

    @Override
    public void setQueryInfo(QueryInfo queryInfo) {
        super.setQueryInfo(queryInfo);
        clause.setQueryInfo(queryInfo);
    }
}
