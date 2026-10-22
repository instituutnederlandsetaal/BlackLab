package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.search.TextPatternContext;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Ensures its string argument is interpreted as a symbol. */
public class QueryFunctionSymbol extends QueryFunction {
    public QueryFunctionSymbol() {
        super("symbol", "Interprets its parameter as a symbol",
                List.of(PString.identifier("name", true)),
                null, false);
    }

    public TextPattern.EvalResult applyFunc(TextPatternContext context, List<Object> parameters) {
        String name = (String)parameters.get(0);
        return ConstraintValue.symbol(name);
    }

}
