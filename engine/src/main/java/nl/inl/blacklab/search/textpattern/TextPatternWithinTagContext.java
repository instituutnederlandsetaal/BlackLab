package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryWithinShortestRepetition;
import nl.inl.blacklab.search.matchfilter.ConstraintValueList;

/**
 * A TextPattern searching for TextPatterns within the context of a tag.
 *
 * For example, Q within the context=s tags means: find matches for
 * Q, and for each match, find the shortest <s/>+ containing it and capture
 * relation(s) within those tag(s).
 *
 * In other words, the context of the match will be the sentence(s) containing it.
 * Multiple sentences will be captured for a match if it overlaps the boundary
 * between sentences.
 */
public class TextPatternWithinTagContext extends TextPattern {

    public static int PRECEDENCE = 10;

    /** The hits we're (possibly) looking for */
    private final TextPattern producer;

    /** What to filter the hits with */
    private final TextPattern filterUnit;

    /** What to capture the context as */
    private final String captureAs;

    public TextPatternWithinTagContext(TextPattern producer, TextPattern filterUnit, String captureAs) {
        super(PRECEDENCE);
        this.producer = producer;
        this.filterUnit = filterUnit;
        this.captureAs = captureAs;
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery {
        EvalResult result = filterUnit.evaluate(context);
        if (result instanceof BLSpanQuery filterQuery) {
            return new SpanQueryWithinShortestRepetition(context.queryInfo(), producer.toQuery(context), filterQuery,
                    context.withRelationAnnotation().luceneFieldRef(), captureAs);
        } else if (result instanceof ConstraintValueList cvl) {
            // Apply multiple filters in sequence
            // Example: A containing list(B, C) -> (A containing B) containing C
            BLSpanQuery resultQuery = producer.toQuery(context);
            for (Object item: cvl.getValue()) {
                if (item instanceof BLSpanQuery filterQuery) {
                    resultQuery = new SpanQueryWithinShortestRepetition(context.queryInfo(), resultQuery, filterQuery,
                            context.withRelationAnnotation().luceneFieldRef(), captureAs);
                } else {
                    throw new InvalidQuery("Non-query filter parameter to 'within tag context' filter: " + item);
                }
            }
            return resultQuery;
        } else {
            throw new InvalidQuery("Right-hand side of 'within tag context' filter must be a query or list of queries: "
                    + result);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternWithinTagContext that = (TextPatternWithinTagContext) o;
        return Objects.equals(producer, that.producer) && Objects.equals(filterUnit, that.filterUnit)
                && Objects.equals(captureAs, that.captureAs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(producer, filterUnit, captureAs);
    }

    @Override
    public String toString() {
        return "WITHIN-TAG-CTX(" + producer + ", " + filterUnit + ", " + captureAs + ")";
    }

    public TextPattern getProducer() {
        return producer;
    }

    public TextPattern getFilter() {
        return filterUnit;
    }

    @Override
    public boolean isRelationsQuery() {
        return producer.isRelationsQuery() || filterUnit.isRelationsQuery();
    }
}
