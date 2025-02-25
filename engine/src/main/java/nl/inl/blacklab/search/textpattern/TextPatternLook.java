package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAdjustHits;
import nl.inl.blacklab.search.lucene.SpanQueryEdge;
import nl.inl.blacklab.search.lucene.SpanQueryNot;

/**
 * Performs (negative) lookahead/lookbehind.
 *
 * Note that the results of this query are zero-length spans.
 */
public class TextPatternLook extends TextPattern {

    private final TextPattern clause;

    private final boolean behind;

    private final boolean negate;

    public TextPatternLook(TextPattern clause, boolean behind, boolean negate) {
        this.clause = clause;
        this.behind = behind;
        this.negate = negate;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        BLSpanQuery result = new SpanQueryEdge(clause.translate(context), behind);
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
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternLook that = (TextPatternLook) o;
        return behind == that.behind && negate == that.negate && Objects.equals(clause, that.clause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clause, behind, negate);
    }

    @Override
    public String toString() {
        String optNegate = negate ? ", NOT" : "";
        return "LOOK(" + (behind ? "behind" : "ahead") + optNegate + ", " + clause + ")";
    }

    public TextPattern getClause() {
        return clause;
    }

    public boolean isLookBehind() {
        return behind;
    }

    public boolean isNegate() {
        return negate;
    }

    @Override
    public boolean isRelationsQuery() {
        return clause.isRelationsQuery();
    }
}
