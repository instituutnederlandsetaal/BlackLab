package nl.inl.blacklab.search.extensions;

import java.util.List;

import org.apache.lucene.queries.spans.BLSpanOrQuery;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.ExprType;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Implements the union function that combines clauses using OR */
public class QueryFunctionUnion extends QueryFunction {
    public QueryFunctionUnion() {
        super("union", List.of(ExprType.LIST),
                null, false);
    }

    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        List<?> list = (List<?>)parameters.get(0);
        BLSpanQuery[] clauses = new BLSpanQuery[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof BLSpanQuery x)
                clauses[i] = x;
            else
                throw new InvalidQuery("Non-query parameter to union(): " + item);
        }
        return new BLSpanOrQuery(clauses);
    }
}
