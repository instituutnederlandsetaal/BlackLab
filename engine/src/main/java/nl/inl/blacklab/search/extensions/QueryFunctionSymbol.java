package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.ExprType;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;

/** Ensures its string argument is interpreted as a symbol. */
public class QueryFunctionSymbol extends QueryFunction {
    public QueryFunctionSymbol() {
        super("symbol", List.of(ExprType.STRING),
                null, false);
    }

    public ConstraintValueSymbol applyFunc(QueryExecutionContext context, List<Object> parameters) {
        String name = (String)parameters.get(0);
        return ConstraintValue.symbol(name);
    }
}
