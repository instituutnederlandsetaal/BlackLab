package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PInteger;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Calculate the absolute value of a number */
public class QueryFunctionAbs extends QueryFunction {
    public QueryFunctionAbs() {
        super("abs", "Calculates the absolute value of a number",
                List.of(PInteger.any("number")),
                null, false);
    }

    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        Integer number = (Integer)parameters.get(0);
        return ConstraintValue.get(Math.abs(number));
    }
}
