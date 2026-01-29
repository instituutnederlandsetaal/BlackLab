package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.ExprType;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueInt;

/** Get start position of span (matchfilter) */
public class QueryFunctionStart extends QueryFunction {
    public QueryFunctionStart() {
        super("start", List.of(ExprType.MATCH_INFO),
                null, false);
    }

    public ConstraintValueInt applyFunc(QueryExecutionContext context, List<Object> parameters) {
        return ConstraintValue.get(((MatchInfo)parameters.get(0)).getSpanStart());
    }
}
