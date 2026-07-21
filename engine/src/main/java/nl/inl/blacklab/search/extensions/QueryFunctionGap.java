package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PBoolean;
import nl.inl.blacklab.plugins.param.PMatchInfo;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Calculate the gap between two captures */
public class QueryFunctionGap extends QueryFunction {
    public QueryFunctionGap() {
        super("gap", "Determine the gap between two captures",
                List.of(PMatchInfo.required("first"), PMatchInfo.required("second"), PBoolean.optional("directional")),
                null, false);
    }

    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        MatchInfo first = (MatchInfo)parameters.get(0);
        MatchInfo second = (MatchInfo)parameters.get(1);
        boolean directional = (boolean)parameters.get(2);

        int gap;
        if (first.getSpanEnd() <= second.getSpanStart()) {
            gap = second.getSpanStart() - first.getSpanEnd();
        } else if (second.getSpanEnd() <= first.getSpanStart()) {
            gap = (directional ? -1 : 1) * (first.getSpanStart() - second.getSpanEnd());
        } else {
            // Overlapping spans, gap is 0
            gap = 0;
        }

        return ConstraintValue.get(gap);
    }

}
