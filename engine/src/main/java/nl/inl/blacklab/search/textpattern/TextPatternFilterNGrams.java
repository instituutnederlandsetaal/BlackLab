package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFilterNGrams;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter.Operation;

public class TextPatternFilterNGrams extends TextPattern {
    protected final TextPattern clause;

    protected final Operation operation;

    /*
     * The minimum hit length
     */
    protected final int min;

    /*
     * The maximum hit length
     */
    protected final int max;

    public TextPatternFilterNGrams(TextPattern clause, Operation operation, int min, int max) {
        this.clause = clause;
        this.operation = operation;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 0 || this.max < 0)
            throw new IllegalArgumentException("min or max can't be negative");
        if (min == 0 && max == 0)
            throw new IllegalArgumentException("not really ngrams (min == max == 0)");
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryFilterNGrams(clause.translate(context), operation, min, max, 0, 0);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternFilterNGrams tp) {
            return clause.equals(tp.clause) && operation == tp.operation && min == tp.min && max == tp.max;
        }
        return false;
    }

    public Operation getOperation() {
        return operation;
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
    public int hashCode() {
        return clause.hashCode() + operation.hashCode() + 13 * min + 31 * max;
    }

    @Override
    public String toString() {
        return "FILTERNGRAMS(" + clause + ", " + operation + ", " + min + ", " + BLSpanQuery.inf(max) + ")";
    }

    @Override
    public boolean isRelationsQuery() {
        return clause.isRelationsQuery();
    }
}
