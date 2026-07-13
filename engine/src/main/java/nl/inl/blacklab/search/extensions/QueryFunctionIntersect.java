package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PList;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Implements the union function that combines clauses using OR */
public class QueryFunctionIntersect extends QueryFunction {
    public QueryFunctionIntersect() {
        super("intersect", List.of(PList.required("clauses", PList.Validator.ALL_QUERIES)),
            null, false);
    }

    // TODO: PluginParams instead of parameters...? But ordered vs. named issue.
    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        List<?> list = (List<?>)parameters.get(0);
        BLSpanQuery[] clauses = new BLSpanQuery[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof BLSpanQuery x)
                clauses[i] = x;
            else
                throw new InvalidQuery("Non-query parameter to intersect(): " + item);
        }
        return new SpanQueryAnd(clauses);
    }

}
