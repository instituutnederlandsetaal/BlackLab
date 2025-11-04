package nl.inl.blacklab.search.extensions;

import java.util.Arrays;
import java.util.List;

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
        super("_fixed", ARGS_SS, Arrays.asList(null, null), false);
    }

    public BLSpanQuery applyFunc(QueryExecutionContext context, List<Object> parameters) {
        int start = Integer.parseInt((String) parameters.get(0));
        int end = Integer.parseInt((String) parameters.get(1));
        return new SpanQueryFixedSpan(context.queryInfo(), context.luceneField(), start, end);
    }
}
