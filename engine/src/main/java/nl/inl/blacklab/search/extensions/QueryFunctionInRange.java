package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PInteger;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Check if a number is within a range */
public class QueryFunctionInRange extends QueryFunction {
    public QueryFunctionInRange() {
        super("in_range",
                List.of(PInteger.any("number"), PInteger.any("min"), PInteger.any("max")),
                null, false);
    }

    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        Integer number = (Integer)parameters.get(0);
        Integer min = (Integer)parameters.get(1);
        Integer max = (Integer)parameters.get(2);
        return ConstraintValue.get(number >= min && number <= max);
    }

}
