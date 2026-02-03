package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.ExprType;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryEdge;
import nl.inl.blacklab.search.lucene.SpanQueryFilterNGrams;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;
import nl.inl.blacklab.search.lucene.SpanQuerySequence;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Implements the SkE meet function that finds hits for a clause
 * that have a second clause in a defined context window before or after the clause hit.
 */
public class QueryFunctionMeet extends QueryFunction {
    public QueryFunctionMeet() {
        super("meet", List.of(ExprType.QUERY, ExprType.QUERY, ExprType.INTEGER, ExprType.INTEGER),
                null, false);
    }

    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        BLSpanQuery findClause = (BLSpanQuery) parameters.get(0);
        BLSpanQuery nearClause = (BLSpanQuery) parameters.get(1);
        int contextStart = (Integer) parameters.get(2);
        int contextEnd = (Integer) parameters.get(3);
        BLSpanQuery result;
        if (contextStart > contextEnd)
            throw new IllegalArgumentException("In meet(), context start cannot be greater than context end");
        if (contextEnd < 0) {
            // Both negative
            // e.g. meet("find", "near", -5, -2) means near is 2-5 tokens before find
            // query becomes: (?<= ([]{4,4) containing "near") []{1} ) "find"
            int skipTokens = -contextEnd - 1;
            int contextSize = contextEnd - contextStart + 1;
            BLSpanQuery ngramsContaining = new SpanQueryFilterNGrams(nearClause,
                    SpanQueryPositionFilter.Operation.CONTAINING, contextSize, contextSize,
                    0, 0);
            BLSpanQuery before = ngramsContaining;
            if (skipTokens > 0) {
                // There's a few tokens after the ngram containing nearClause (before the findClause hit)
                // (i.e. contextStart < -1)
                SpanQueryAnyToken tokensToSkip = new SpanQueryAnyToken(context.queryInfo(), skipTokens,
                        skipTokens, context.luceneField());
                before = new SpanQuerySequence(ngramsContaining, tokensToSkip);
            }
            // Take only the trailing edge of the lookbehind clause hits
            BLSpanQuery lookbehindEdge = new SpanQueryEdge(before, true);
            // Find the findClause hits where the lookahead clause occurs direct after it
            result = new SpanQuerySequence(lookbehindEdge, findClause);
        } else if (contextStart >= 0) {
            // Both non-negative
            // e.g. meet("find", "near", 2, 5) means near is 2-5 tokens after find
            // query becomes: "find" (?= []{1} ([]{4,4) containing "near"))
            if (contextStart == 0)
                contextStart = 1;
            if (contextEnd == 0)
                contextEnd = 1;
            int skipTokens = contextStart - 1;
            int contextSize = contextEnd - contextStart + 1;
            BLSpanQuery ngramsContaining = new SpanQueryFilterNGrams(nearClause,
                    SpanQueryPositionFilter.Operation.CONTAINING, contextSize, contextSize,
                    0, 0);
            BLSpanQuery after = ngramsContaining;
            if (skipTokens > 0) {
                // Skip a few tokens after the findClause hit before looking for the ngram containing nearClause
                // (i.e. contextStart > 1)
                SpanQueryAnyToken tokensToSkip = new SpanQueryAnyToken(context.queryInfo(), skipTokens,
                        skipTokens, context.luceneField());
                after = new SpanQuerySequence(tokensToSkip, ngramsContaining);
            }
            // Take only the leading edge of the lookahead clause hits
            BLSpanQuery lookaheadEdge = new SpanQueryEdge(after, false);
            // Find the findClause hits where the lookahead clause occurs direct after it
            result = new SpanQuerySequence(findClause, lookaheadEdge);
        } else {
            // Start negative, end non-negative
            // Just use containing with adjusted context
            result = new SpanQueryPositionFilter(findClause, nearClause,
                    SpanQueryPositionFilter.Operation.CONTAINING, false, contextStart,
                    contextEnd);
        }
        return result;
    }
}
