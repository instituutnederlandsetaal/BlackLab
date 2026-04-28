package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PluginParam;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** A QueryFunction instantiated by passing it a lambda. */
public class QueryFunctionLambda extends QueryFunction {

    private final ExtensionFunction func;

    public QueryFunctionLambda(String name, ExtensionFunction func, List<PluginParam> argTypes,
            List<Object> defaultValues, boolean relationsFunction) {
        super(name, argTypes, defaultValues, relationsFunction);
        this.func = func;
    }

    @Override
    protected TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        return func.apply(context.queryInfo(), context, parameters);
    }

    @Override
    public boolean isWebSafe() {
        return true;
    }
}
