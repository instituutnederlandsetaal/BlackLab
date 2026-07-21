package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PList;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Creates a list from its arguments. */
public class QueryFunctionList extends QueryFunction {
    public QueryFunctionList() {
        // Takes a single argument of type LIST and just returns it
        // (this works because LIST is automatically interpreted as a variable number of arguments)
        super("list", "Creates a list of values",
                List.of(PList.required("list", (l) -> {})),
                null, false);
    }

    @SuppressWarnings("unchecked")
    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        return ConstraintValue.get((List<Object>)parameters.get(0));
    }

}
