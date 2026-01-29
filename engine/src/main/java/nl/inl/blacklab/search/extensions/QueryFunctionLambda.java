package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.ExprType;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.search.QueryExecutionContext;

/** A QueryFunction instantiated by passing it a lambda. */
public class QueryFunctionLambda extends QueryFunction {

    private final ExtensionFunction func;

    public QueryFunctionLambda(String name, ExtensionFunction func, List<ExprType> argTypes,
            List<Object> defaultValues, boolean relationsFunction) {
        super(name, argTypes, defaultValues, relationsFunction);
        this.func = func;
    }

    @Override
    protected Object applyFunc(QueryExecutionContext context, List<Object> parameters) {
        return func.apply(context.queryInfo(), context, parameters);
    }
}
