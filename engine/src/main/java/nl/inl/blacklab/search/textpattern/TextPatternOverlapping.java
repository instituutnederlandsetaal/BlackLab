package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryOverlapping;

/**
 * A TextPattern operating on two overlapping patterns.
 *
 * E.g. can find the overlaps between hits from two queries.
 */
public class TextPatternOverlapping extends TextPattern {

    /** The hits we're (possibly) looking for */
    private final TextPattern left;

    /** What to filter the hits with */
    private final TextPattern right;

    /** Operation to use (e.g. overlap) */
    private final String operation;

    public TextPatternOverlapping(TextPattern left, TextPattern right, String operation) {
        this.left = left;
        this.right = right;
        this.operation = operation;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryOverlapping(left.translate(context), right.translate(context),
                operation);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternOverlapping that = (TextPatternOverlapping) o;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right)
                && Objects.equals(operation, that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right, operation);
    }
    @Override
    public String toString() {
        return "OVERLAPPING(" + left + ", " + right + ", " + operation + ")";
    }

    public TextPattern getLeft() {
        return left;
    }

    public TextPattern getRight() {
        return right;
    }

    public String getOperation() {
        return operation;
    }

    @Override
    public boolean isRelationsQuery() {
        return left.isRelationsQuery() || right.isRelationsQuery();
    }
}
