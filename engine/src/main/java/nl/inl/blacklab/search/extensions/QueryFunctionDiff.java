package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PInteger;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Calculate difference between two numbers */
public class QueryFunctionDiff extends QueryFunction {
    public QueryFunctionDiff() {
        super("diff",
                List.of(PInteger.any("first"), PInteger.any("second")),
                null, false);
    }

    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        Integer first = (Integer)parameters.get(0);
        Integer second = (Integer)parameters.get(1);
        return ConstraintValue.get(first - second);
    }

}
