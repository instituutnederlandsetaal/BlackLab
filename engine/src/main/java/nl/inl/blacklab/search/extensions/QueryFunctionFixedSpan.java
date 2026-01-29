package nl.inl.blacklab.search.extensions;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.plugins.ExprType;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFixedSpan;

/** A fixed span in every matching doc.
 * <p>
 * E.g. _fixed("0", "7") find tokens 0 (inclusive) to 7 (exclusive) in every doc.
 * This is also an example of how you can implement QueryFunction in a plugin JAR (or Groovy script).
 */
public class QueryFunctionFixedSpan extends QueryFunction {
    public QueryFunctionFixedSpan() {
        super("_fixed", List.of(ExprType.INTEGER, ExprType.INTEGER), Arrays.asList(null, null), false);
    }

    public BLSpanQuery applyFunc(QueryExecutionContext context, List<Object> parameters) {
        int start = (Integer) parameters.get(0);
        int end = (Integer) parameters.get(1);
        if (start < 0 || end < 0)
            throw new IllegalArgumentException("_fixed() takes non-negative integers as arguments");
        if (end < start)
            throw new IllegalArgumentException("_end must be greater than or equal to _start in _fixed()");
        return new SpanQueryFixedSpan(context.queryInfo(), context.luceneField(), start, end);
    }
}
