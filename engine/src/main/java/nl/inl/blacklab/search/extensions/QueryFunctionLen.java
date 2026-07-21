package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PAny;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Determines the length of a list, string, matchinfo or intrange. */
public class QueryFunctionLen extends QueryFunction {
    public QueryFunctionLen() {
        // Takes a single argument of type LIST and just returns it
        // (this works because LIST is automatically interpreted as a variable number of arguments)
        super("len", "Determine the length of its parameter",
                List.of(PAny.required("value")),
                null, false);
    }

    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        int result;
        Object o = parameters.get(0);
        if (o == null) {
            result = 0;
        } else if (o instanceof List l) {
            result = l.size();
        } else if (o instanceof String s) {
            result = s.length();
        } else if (o instanceof MatchInfo m) {
            result = m.getSpanEnd() - m.getSpanStart();
        } else if (o instanceof Integer[] arr) {
            result = arr[1] - arr[0];
        } else {
            throw new InvalidQuery("Argument to len() must be a list or string, got: " + o);
        }
        return ConstraintValue.get(result);
    }

}
