package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;
import nl.inl.blacklab.search.matchfilter.ConstraintValueList;

/**
 * A TextPattern searching for TextPatterns that contain a hit from another
 * TextPattern. This may be used to search for sentences containing a certain
 * word, etc.
 */
public class TextPatternPositionFilter extends TextPattern {

    public static int TP_PRECEDENCE = 10;

    /** The hits we're (possibly) looking for */
    private final TextPattern producer;

    /** What to filter the hits with */
    private final TextPattern filter;

    /** Operation to use for filtering (e.g. within/containing/...) */
    private final SpanQueryPositionFilter.Operation operation;

    /** Whether to invert the filter operation */
    private final boolean invert;

    /** How to adjust the leading edge of the producer hits while matching */
    private final int adjustLeading;

    /** How to adjust the trailing edge of the producer hits while matching */
    private final int adjustTrailing;

    public TextPatternPositionFilter(TextPattern producer, TextPattern filter, SpanQueryPositionFilter.Operation operation) {
        this(producer, filter, operation, false, 0, 0);
    }

    public TextPatternPositionFilter(TextPattern producer, TextPattern filter, SpanQueryPositionFilter.Operation operation,
            boolean invert, int adjustLeading, int adjustTrailing) {
        super(TP_PRECEDENCE);
        this.producer = producer;
        this.filter = filter;
        this.operation = operation;
        this.invert = invert;
        this.adjustLeading = adjustLeading;
        this.adjustTrailing = adjustTrailing;
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery {
        EvalResult result = filter.evaluate(context);
        if (result instanceof BLSpanQuery filterQuery) {
            return new SpanQueryPositionFilter(producer.toQuery(context), filterQuery,
                    operation, invert, adjustLeading, adjustTrailing);
        } else if (result instanceof ConstraintValueList cvl) {
            // Apply multiple filters in sequence
            // Example: A containing list(B, C) -> (A containing B) containing C
            BLSpanQuery resultQuery = producer.toQuery(context);
            for (Object item: cvl.getValue()) {
                if (item instanceof BLSpanQuery filterQuery) {
                    resultQuery = new SpanQueryPositionFilter(resultQuery, filterQuery,
                            operation, invert, adjustLeading, adjustTrailing);
                } else {
                    throw new InvalidQuery("Non-query filter parameter to position filter " + operation + ": " + item);
                }
            }
            return resultQuery;
        } else {
            throw new InvalidQuery("Right-hand side of position filter " + operation +
                    " must be a query or list of queries: " + result);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternPositionFilter that = (TextPatternPositionFilter) o;
        return invert == that.invert && adjustLeading == that.adjustLeading && adjustTrailing == that.adjustTrailing
                && Objects.equals(producer, that.producer) && Objects.equals(filter, that.filter)
                && operation == that.operation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(producer, filter, operation, invert, adjustLeading, adjustTrailing);
    }

    @Override
    public String toString() {
        String adj = (adjustLeading != 0 || adjustTrailing != 0 ?
                ", " + adjustLeading + ", " + adjustTrailing : "");
        return "POSFILTER(" + producer + ", " + filter + ", " +
                (invert ? "NOT" : "") + operation + adj + ")";
    }

    /**
     * Is this a "within tag" operation?
     *
     * Used if context is set to the tag to determine if we need to add "within <TAGNAME/>" to the query or not.
     *
     * @param tagName the tag name to check
     * @return true if this is a "within tag" operation and the tag name matches
     */
    public boolean isWithinTag(String tagName) {
        if (operation != SpanQueryPositionFilter.Operation.WITHIN)
            return false;
        boolean isCorrectTag = filter instanceof TextPatternTags &&
                ((TextPatternTags)filter).getElementNameRegex().equals(tagName);
        return isCorrectTag && adjustLeading == 0 && adjustTrailing == 0;
    }

    public TextPattern getProducer() {
        return producer;
    }

    public TextPattern getFilter() {
        return filter;
    }

    public SpanQueryPositionFilter.Operation getOperation() {
        return operation;
    }

    public boolean isInvert() {
        return invert;
    }

    public int getAdjustLeading() {
        return adjustLeading;
    }

    public int getAdjustTrailing() {
        return adjustTrailing;
    }

    @Override
    public boolean isRelationsQuery() {
        return producer.isRelationsQuery() || filter.isRelationsQuery() && !invert;
    }

    @Override
    public <T> T accept(TextPatternVisitor<T> visitor) {
        return visitor.visitPositionFilter(this);
    }
}
