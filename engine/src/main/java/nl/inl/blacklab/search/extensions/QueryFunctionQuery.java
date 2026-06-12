package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Ensures its string argument is interpreted as a query. */
public class QueryFunctionQuery extends QueryFunction {
    public QueryFunctionQuery() {
        super("query", List.of(PQuery.required("query")),
                null, false);
    }

    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        Object o = parameters.get(0);
        if (o instanceof BLSpanQuery q)
            return q;
        throw new IllegalArgumentException("Argument to query() must be a query, got: " + o);
    }

}
