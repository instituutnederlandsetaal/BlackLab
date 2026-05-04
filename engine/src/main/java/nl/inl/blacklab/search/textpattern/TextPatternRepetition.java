package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryRepetition;

/**
 * Repetition of a pattern.
 */
public class TextPatternRepetition extends TextPattern {

    public static int TP_PRECEDENCE = 3;

    public static TextPattern get(TextPattern clause, int min, int max) {
        if (min == 0 && max == 0)
            throw new IllegalArgumentException("min == max == 0");
        if (min == 1 && max == 1)
            return clause;
        return new TextPatternRepetition(clause, min, max);
    }

    private final TextPattern clause;

    private final int min;

    private final int max;

    public TextPatternRepetition(TextPattern clause, int min, int max) {
        super(TP_PRECEDENCE);
        this.clause = clause;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (this.min > this.max)
            throw new IllegalArgumentException("min > max");
        if (this.min < 0)
            throw new IllegalArgumentException("min or max can't be negative");
        if (this.min == this.max && (this.min == 0 || this.min == 1))
            throw new IllegalArgumentException("not really a repetition (min == max == " + min + ")");
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery {
        BLSpanQuery baseTranslated = clause.toQuery(context);

        if (min == 1 && max == 1)
            return baseTranslated; // no repetition

        return new SpanQueryRepetition(baseTranslated, min, max);
    }

    public TextPattern getClause() {
        return clause;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternRepetition tp) {
            return clause.equals(tp.clause) && min == tp.min && max == tp.max;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return clause.hashCode() + 13 * min + 31 * max;
    }

    @Override
    public String toString() {
        return "REP(" + clause.toString() + ", " + min + ", " + BLSpanQuery.inf(max) + ")";
    }

    @Override
    public boolean isRelationsQuery() {
        return clause.isRelationsQuery();
    }

    @Override
    public <T> T accept(TextPatternVisitor<T> visitor) {
        return visitor.visitRepetition(this);
    }
}
