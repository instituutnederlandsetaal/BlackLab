package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.ExprType;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueList;

/** Creates a list from its arguments. */
public class QueryFunctionList extends QueryFunction {
    public QueryFunctionList() {
        // Takes a single argument of type LIST and just returns it
        // (this works because LIST is automatically interpreted as a variable number of arguments)
        super("list", List.of(ExprType.LIST),
                null, false);
    }

    @SuppressWarnings("unchecked")
    public ConstraintValueList applyFunc(QueryExecutionContext context, List<Object> parameters) {
        return ConstraintValue.get((List<Object>)parameters.get(0));
    }
}
