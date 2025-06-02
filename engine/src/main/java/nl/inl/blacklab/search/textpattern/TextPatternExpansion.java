package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion.Direction;

public class TextPatternExpansion extends TextPattern {

    protected final TextPattern clause;

    protected final Direction direction;

    /*
     * The minimum number of tokens in this stretch.
     */
    protected final int min;

    /*
     * The maximum number of tokens in this stretch.
     */
    protected final int max;

    public TextPatternExpansion(TextPattern clause, Direction direction, int min, int max) {
        this.clause = clause;
        this.direction = direction;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 0 || this.max < 0)
            throw new IllegalArgumentException("min or max can't be negative");
        if (min == 0 && max == 0)
            throw new IllegalArgumentException("not really an expansion (min == max == 0)");
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryExpansion(clause.translate(context), direction, min, max);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternExpansion tp) {
            return clause.equals(tp.clause) && direction == tp.direction && min == tp.min && max == tp.max;
        }
        return false;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public boolean isExpandToLeft() {
        return direction == Direction.LEFT;
    }

    public TextPattern getClause() {
        return clause;
    }

    @Override
    public int hashCode() {
        return clause.hashCode() + 1023 * direction.hashCode() + 13 * min + 31 * max;
    }

    @Override
    public String toString() {
        return "EXPANSION(" + clause + ", " + direction + ", " + min + ", " + BLSpanQuery.inf(max) + ")";
    }

    private static TextPattern get(TextPattern clause, Direction direction, int min, int max) {
        if (min == 0 && max == 0)
            return clause;
        return new TextPatternExpansion(clause, direction, min, max);
    }

    public Direction getDirection() {
        return direction;
    }

    @Override
    public boolean isRelationsQuery() {
        return clause.isRelationsQuery();
    }
}
