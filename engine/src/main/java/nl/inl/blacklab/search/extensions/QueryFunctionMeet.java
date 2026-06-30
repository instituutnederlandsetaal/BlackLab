package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PInteger;
import nl.inl.blacklab.plugins.param.PQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Implements the SkE meet function that finds hits for a clause
 * that have a second clause in a defined context window before or after the clause hit.
 */
public class QueryFunctionMeet extends QueryFunction {
    public QueryFunctionMeet() {
        super("meet", List.of(
                PQuery.required("first"),
                        PQuery.required("second"),
                        PInteger.any("rangeStart"),
                        PInteger.any("rangeEnd")),
                null, false);
    }

    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        BLSpanQuery findClause = (BLSpanQuery) parameters.get(0);
        BLSpanQuery nearClause = (BLSpanQuery) parameters.get(1);
        int contextStart = (Integer) parameters.get(2);
        int contextEnd = (Integer) parameters.get(3);
        return QueryFunctionMeetWithin.getBlSpanQuery(context, findClause, nearClause, null, contextStart, contextEnd);
    }

}
