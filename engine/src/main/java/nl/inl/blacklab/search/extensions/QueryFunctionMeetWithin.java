package nl.inl.blacklab.search.extensions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.queries.spans.BLSpanOrQuery;
import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PInteger;
import nl.inl.blacklab.plugins.param.PQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanFilter;
import nl.inl.blacklab.search.lucene.SpanQueryAdjustHits;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureGroup;
import nl.inl.blacklab.search.lucene.SpanQueryEdge;
import nl.inl.blacklab.search.lucene.SpanQueryNot;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;
import nl.inl.blacklab.search.lucene.SpanQueryRelationSpanAdjust;
import nl.inl.blacklab.search.lucene.SpanQuerySequence;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Implements the SkE meet function that finds hits for a clause
 * that have a second clause in a defined context window before or after the clause hit.
 */
public class QueryFunctionMeetWithin extends QueryFunction {
    public QueryFunctionMeetWithin() {
        super("meet_within", List.of(
                PQuery.required("first"),
                        PQuery.required("second"),
                        PQuery.required("within"),
                        PInteger.any("rangeStart"),
                        PInteger.any("rangeEnd")),
                Arrays.asList(null, null, null, 0, 0), false);
    }

    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        BLSpanQuery findClause = (BLSpanQuery) parameters.get(0);
        BLSpanQuery nearClause = (BLSpanQuery) parameters.get(1);
        BLSpanQuery withinClause = (BLSpanQuery) parameters.get(2);
        int contextStart = (Integer) parameters.get(3);
        int contextEnd = (Integer) parameters.get(4);
        return getBlSpanQuery(context, findClause, nearClause, withinClause, contextStart, contextEnd);
    }

    static @NonNull BLSpanQuery getBlSpanQuery(QueryExecutionContext context, BLSpanQuery findClause,
            BLSpanQuery nearClause, BLSpanQuery withinClause,
            int contextStart, int contextEnd) {
        if (withinClause == null) {
            // No within clause. Proximity must be specified. Correct 0 values (which are invalid for meet())
            // to valid ones.
            if (contextStart == 0)
                contextStart = -1;
            if (contextEnd == 0)
                contextEnd = 1;
        }
        if (contextStart > contextEnd)
            throw new IllegalArgumentException("In meet/meet_within, context start cannot be greater than context end");
        boolean negate = false;
        if (nearClause instanceof SpanQueryNot sqn) {
            // We're looking for find clause where near clause does NOT occur nearby!
            negate = true;
            nearClause = sqn.inverted();
        }

        if (contextStart == 0 && contextEnd == 0) {
            // We don't care about proxity as long as both words are within a span, e.g. a sentence.
            // e.g. meet_within("tree", "leaf", <s/>) =>
            // "tree" within (<s/> containing "leaf")
            SpanQueryPositionFilter where = new SpanQueryPositionFilter(withinClause, nearClause,
                    SpanFilter.CONTAINING, negate);
            return new SpanQueryPositionFilter(findClause, where,
                    SpanFilter.WITHIN, false);
        }

        BLSpanQuery result;
        if (contextEnd <= 0) {
            // Both negative; near before find
            if (contextStart == 0)
                contextStart = -1;
            if (contextEnd == 0)
                contextEnd = -1;
            result = nearBeforeFind(context, findClause, nearClause, negate, contextStart, contextEnd, withinClause);
        } else if (contextStart >= 0) {
            // Both non-negative; near after find
            if (contextStart == 0)
                contextStart = 1;
            result = nearAfterFind(context, findClause, nearClause, negate, contextStart, contextEnd, withinClause);
        } else {
            // Start negative, end non-negative. Near can occur either before or after find. Split into two.
            // e.g. meet("find", "near", -2, 5) == meet("find", "near", -2, -1) | meet("find", "near", 1, 5)
            BLSpanQuery[] clauses = new BLSpanQuery[] {
                nearBeforeFind(context, findClause, nearClause, negate, contextStart, -1, withinClause),
                nearAfterFind(context, findClause, nearClause, negate, 1, contextEnd, withinClause)
            };
            result = negate ? new SpanQueryAnd(clauses) : new BLSpanOrQuery(clauses);
        }
        return result;
    }

    /** A call to meet with context start and end both positive */
    private static @NonNull BLSpanQuery nearAfterFind(QueryExecutionContext context, BLSpanQuery findClause,
            BLSpanQuery nearClause, boolean negateNear, int contextStart, int contextEnd, BLSpanQuery withinClause) {
        // e.g. meet("find", "near", 2, 5) means near is 2-5 tokens after find
        // query becomes: "find" (?= []{1, 4} "near")
        SpanQueryAnyToken gap = contextStart == 1 && contextEnd == 1 ? null :
                new SpanQueryAnyToken(context.queryInfo(), contextStart - 1, contextEnd - 1,
                        context.luceneField());
        List<BLSpanQuery> clauses = new ArrayList<>(List.of(findClause, nearClause));
        if (gap != null)
            clauses.add(1, gap);
        BLSpanQuery positiveMatch = optWrapWithin(new SpanQuerySequence(clauses), withinClause);
        if (negateNear) {
            // We're looking for the find clause NOT near the near clause.
            // First, find all 0-length spans that are NOT the start of a positive match (i.e. find gap near)
            SpanQueryEdge notPositiveHitPositions = SpanQueryEdge.leading(
                    new SpanQueryNot(
                            new SpanQueryAdjustHits(
                                    SpanQueryEdge.leading(positiveMatch),
                                    0, 1
                            )
                    )
            );
            // Now find all matches of the find clause at such a position
            return new SpanQuerySequence(notPositiveHitPositions, findClause);
        } else {
            if ((gap == null || gap.guarantees().hitsAllSameLength()) && nearClause.guarantees().hitsAllSameLength()) {
                // Fixed length gap+near clause. Adjust positiveMatch to get requested find clause.
                int gapNearLength = (gap == null ? 0 : gap.guarantees().hitsLengthMin()) + nearClause.guarantees().hitsLengthMin();
                return new SpanQueryAdjustHits(positiveMatch, 0, -gapNearLength);
            } else if (findClause.guarantees().hitsAllSameLength()) {
                // Fixed-length find clause. Adjust positiveMatch from leading edge to get find clause.
                int findLength = findClause.guarantees().hitsLengthMin();
                SpanQueryEdge leadingEdge = SpanQueryEdge.leading(positiveMatch);
                return new SpanQueryAdjustHits(leadingEdge, 0, findLength);
            } else {
                // Neither side is fixed-length. Introduce a internal-only capture group for the find clause and
                // adjust the hits to this capture group later.
                return findViaCapture(clauses, false, withinClause);
            }
        }
    }

    /** A call to meet with context start and end both negative */
    private static @NonNull BLSpanQuery nearBeforeFind(QueryExecutionContext context, BLSpanQuery findClause,
            BLSpanQuery nearClause, boolean negateNear, int contextStart, int contextEnd, BLSpanQuery withinClause) {
        // e.g. meet("find", "near", -5, -2) means near is 2-5 tokens before find
        // query becomes: (?<= "near" []{1,4} ) "find"
        SpanQueryAnyToken gap = contextStart == -1 && contextEnd == -1 ? null : new SpanQueryAnyToken(context.queryInfo(), -contextEnd - 1, -contextStart - 1,
                context.luceneField());
        List<BLSpanQuery> clauses = new ArrayList<>(List.of(nearClause, findClause));
        if (gap != null)
            clauses.add(1, gap);
        BLSpanQuery positiveMatch = optWrapWithin(new SpanQuerySequence(clauses), withinClause);
        if (negateNear) {
            // We're looking for the find clause NOT near the near clause.
            // First, find all 0-length spans that are NOT the start of a positive match (i.e. find gap near)
            SpanQueryEdge notPositiveHitPositions = SpanQueryEdge.trailing(
                    new SpanQueryNot(
                            new SpanQueryAdjustHits(
                                    SpanQueryEdge.trailing(positiveMatch),
                                    -1, 0
                            )
                    )
            );
            // Now find all matches of the find clause ending at such a position
            return new SpanQuerySequence(findClause, notPositiveHitPositions);
        } else {
            if ((gap == null || gap.guarantees().hitsAllSameLength()) && nearClause.guarantees().hitsAllSameLength()) {
                // Fixed length gap+near clause. Adjust positiveMatch to get requested find clause.
                int gapLength = gap == null ? 0 : gap.guarantees().hitsLengthMin();
                int gapNearLength = gapLength + nearClause.guarantees().hitsLengthMin();
                return new SpanQueryAdjustHits(positiveMatch, gapNearLength, 0);
            } else if (findClause.guarantees().hitsAllSameLength()) {
                // Fixed-length find clause. Adjust positiveMatch from trailing edge to get find clause.
                int findLength = findClause.guarantees().hitsLengthMin();
                SpanQueryEdge trailingEdge = SpanQueryEdge.trailing(positiveMatch);
                return new SpanQueryAdjustHits(trailingEdge, -findLength, 0);
            } else {
                // Neither side is fixed-length. Introduce a internal-only capture group for the find clause and
                // adjust the hits to this capture group later.
                return findViaCapture(clauses, true, withinClause);
            }
        }
    }

    private static @NonNull SpanQueryRelationSpanAdjust findViaCapture(List<BLSpanQuery> findGapNear, boolean nearBeforeFind,
            BLSpanQuery withinClause) {
        // Neither side is fixed-length. Introduce a internal-only capture group for the find clause and
        // adjust the hits to this capture group later.
        findGapNear = new ArrayList<>(findGapNear);
        int findIndex = nearBeforeFind ? findGapNear.size() - 1 : 0;
        BLSpanQuery findClause = findGapNear.get(findIndex);
        SpanQueryCaptureGroup capturedFind = new SpanQueryCaptureGroup(findClause, null);
        findGapNear.set(findIndex, capturedFind);
        BLSpanQuery matchWithCap = optWrapWithin(new SpanQuerySequence(findGapNear), withinClause);
        // Now return the capture as the actual hit
        return new SpanQueryRelationSpanAdjust(matchWithCap, null, null,
                capturedFind.getCaptureName());
    }

    private static BLSpanQuery optWrapWithin(BLSpanQuery clause, BLSpanQuery optWithin) {
        // Optionally apply within clause
        return optWithin == null ? clause :
                new SpanQueryPositionFilter(clause, optWithin, SpanFilter.WITHIN, false);
    }

}
