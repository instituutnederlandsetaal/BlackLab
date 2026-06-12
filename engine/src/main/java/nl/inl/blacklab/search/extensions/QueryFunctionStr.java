package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PAny;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueList;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Ensures its argument is interpreted as a string. */
public class QueryFunctionStr extends QueryFunction {
    public QueryFunctionStr() {
        super("str", List.of(PAny.required("value")),
                null, false);
    }

    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        Object o = parameters.get(0);
        if (o instanceof BLSpanQuery q) {
            String v = TextPattern.getSimpleStringValue(context, q);
            if (v != null)
                return ConstraintValue.get(v);
        } else if (o instanceof ConstraintValueList l) {
            StringBuilder sb = new StringBuilder();
            for (Object item: l.getValue()) {
                if (!sb.isEmpty())
                    sb.append(", ");
                sb.append(applyFunc(context, List.of(item)));
            }
            return ConstraintValue.get(sb.toString());
        }
        return ConstraintValue.get(o.toString());
    }

}
