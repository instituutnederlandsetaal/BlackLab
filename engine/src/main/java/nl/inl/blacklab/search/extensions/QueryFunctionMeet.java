package nl.inl.blacklab.search.extensions;

import java.util.List;

import org.apache.lucene.queries.spans.BLSpanOrQuery;
import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PInteger;
import nl.inl.blacklab.plugins.param.PQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryEdge;
import nl.inl.blacklab.search.lucene.SpanQueryNot;
import nl.inl.blacklab.search.lucene.SpanQuerySequence;
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
        if (contextStart > contextEnd)
            throw new IllegalArgumentException("In meet(), context start cannot be greater than context end");
        BLSpanQuery result;
        boolean negate = false;
        if (nearClause instanceof SpanQueryNot sqn) {
            // We're looking for find clause where near clause does NOT occur nearby!
            negate = true;
            nearClause = sqn.inverted();
        }
        if (contextEnd <= 0) {
            // Both negative; near before find
            if (contextStart == 0)
                contextStart = -1;
            if (contextEnd == 0)
                contextEnd = -1;
            result = nearBeforeFind(context, findClause, nearClause, negate, contextStart, contextEnd);
        } else if (contextStart >= 0) {
            // Both non-negative; near after find
            if (contextStart == 0)
                contextStart = 1;
            if (contextEnd == 0)
                contextEnd = 1;
            result = nearAfterFind(context, findClause, nearClause, negate, contextStart, contextEnd);
        } else {
            // Start negative, end non-negative. Near can occur either before or after find. Split into two.
            // e.g. meet("find", "near", -2, 5) == meet("find", "near", -2, -1) | meet("find", "near", 1, 5)
            BLSpanQuery[] clauses = new BLSpanQuery[] {
                    nearBeforeFind(context, findClause, nearClause, negate, contextStart, -1),
                    nearAfterFind(context, findClause, nearClause, negate, 1, contextEnd)
            };
            result = negate ? new SpanQueryAnd(clauses) : new BLSpanOrQuery(clauses);
        }
        return result;
    }

    /** A call to meet with context start and end both negative */
    private static @NonNull BLSpanQuery nearBeforeFind(QueryExecutionContext context, BLSpanQuery findClause,
            BLSpanQuery nearClause, boolean negateNear, int contextStart, int contextEnd) {
        // e.g. meet("find", "near", -5, -2) means near is 2-5 tokens before find
        // query becomes: (?<= "near" []{1,4} ) "find"
        SpanQueryAnyToken tokensToSkip = new SpanQueryAnyToken(context.queryInfo(), -contextEnd - 1,
                -contextStart - 1, context.luceneField());
        BLSpanQuery before = new SpanQuerySequence(nearClause, tokensToSkip);
        // Take only the trailing edge of the lookbehind clause hits
        BLSpanQuery lookbehindEdge = SpanQueryEdge.lookAheadBehindQuery(before, true, negateNear);
        // Find the findClause hits where the lookahead clause occurs direct after it
        return new SpanQuerySequence(lookbehindEdge, findClause);
    }

    /** A call to meet with context start and end both positive */
    private static @NonNull BLSpanQuery nearAfterFind(QueryExecutionContext context, BLSpanQuery findClause,
            BLSpanQuery nearClause, boolean negateNear, int contextStart, int contextEnd) {
        // e.g. meet("find", "near", 2, 5) means near is 2-5 tokens after find
        // query becomes: "find" (?= []{1, 4} "near")
        SpanQueryAnyToken tokensToSkip = new SpanQueryAnyToken(context.queryInfo(), contextStart - 1,
                contextEnd - 1, context.luceneField());
        BLSpanQuery after = new SpanQuerySequence(tokensToSkip, nearClause);
        // Take only the leading edge of the lookahead clause hits
        BLSpanQuery lookaheadEdge = SpanQueryEdge.lookAheadBehindQuery(after, false, negateNear);
        // Find the findClause hits where the lookahead clause occurs direct after it
        return new SpanQuerySequence(findClause, lookaheadEdge);
    }

}
