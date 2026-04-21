package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.SpanQueryEdge;

/**
 * Performs (negative) lookahead/lookbehind.
 *
 * Note that the results of this query are zero-length spans.
 */
public class TextPatternLook extends TextPattern {

    public static int TP_PRECEDENCE = 0;

    private final TextPattern clause;

    private final boolean behind;

    private final boolean negate;

    public TextPatternLook(TextPattern clause, boolean behind, boolean negate) {
        super(TP_PRECEDENCE);
        this.clause = clause;
        this.behind = behind;
        this.negate = negate;
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery {
        return SpanQueryEdge.lookAheadBehindQuery(clause.toQuery(context), behind, negate);
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
