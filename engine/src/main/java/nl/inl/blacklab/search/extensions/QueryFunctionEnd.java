package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PMatchInfo;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueInt;

/** Get end position of span (matchfilter) */
public class QueryFunctionEnd extends QueryFunction {
    public QueryFunctionEnd() {
        super("end", "Gets the end position of a span",
                List.of(PMatchInfo.required("matchInfo")),
                null, false);
    }

    public ConstraintValueInt applyFunc(QueryExecutionContext context, List<Object> parameters) {
        return ConstraintValue.get(((MatchInfo)parameters.get(0)).getSpanEnd());
    }

}
