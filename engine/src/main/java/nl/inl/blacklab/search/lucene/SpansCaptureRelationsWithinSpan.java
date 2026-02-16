package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queries.spans.FilterSpans;

/**
 * Captures all relations within a match info span.
 *
 * Note that the match info spans we're trying to capture relations in
 * may not overlap! If they do overlap, some relations may be skipped over.
 * TODO: use SpansInBuckets to fix this.
 */
class SpansCaptureRelationsWithinSpan extends BLFilterSpans<BLSpans> {

    private final BLSpans relations;

    /** Name of match info spans to capture relations from
     *  (if not set, capture all relations in current clause hit) */
    final String toCapture;

    /** Group index of toCapture */
    private int toCaptureIndex = -1;

    /** Match info name for the list of captured relations */
    final String captureAs;

    /** Group index of captureAs */
    private int captureAsIndex = -1;

    /** Our hit query context */
    private HitQueryContext context;

    /** Match info for current hit */
    private MatchInfo[] matchInfo;

    /** List of relations captured for current hit */
    private final List<RelationInfo> capturedRelations = new ArrayList<>();

    /**
     * Construct a SpansCaptureRelationsWithinSpan.
     *
     * if toCapture is not set, capture all relations in current clause hit
     *
     * @param clause clause we're capturing from
     * @param relations relations to capture
     * @param toCapture name of the match info span to capture relations from
     * @param captureAs name to capture the list of relations as
     */
    public SpansCaptureRelationsWithinSpan(BLSpans clause, BLSpans relations, String toCapture, String captureAs) {
        super(clause);
        this.relations = relations;
        this.toCapture = toCapture;
        this.captureAs = captureAs;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        if (matchInfo == null) {
            matchInfo = new MatchInfo[context.numberOfMatchInfos()];
        } else {
            Arrays.fill(matchInfo, null);
        }

        // We can "only" get match info for our own clause, but that should be enough
        // (we can only capture relations from match info captured within own clause)
        candidate.getMatchInfo(matchInfo);
        int start = -1, end = -1;
        if (toCaptureIndex >= 0) {
            // Capture relations inside a named captured span
            MatchInfo mi = matchInfo[toCaptureIndex];
            if (mi != null) { // We can only capture relations if we have the span
                start = mi.getSpanStart();
                end = mi.getSpanEnd();
            }
        } else {
            // Just use the current clause hit.
            start = in.startPosition();
            end = in.endPosition();
        }

        if (start >= 0) {
            captureRelationsWithinSpan(candidate.docID(), start, end, relations, capturedRelations);
            matchInfo[captureAsIndex] = RelationListInfo.create(capturedRelations, getOverriddenField());
        }

        return FilterSpans.AcceptStatus.YES;
    }

    public static void captureRelationsWithinSpan(int docId, int start, int end, BLSpans relations, List<RelationInfo> capturedRelations) throws IOException {
        // Capture all relations within the toCapture span
        capturedRelations.clear();
        int relDocId = relations.docID();
        if (relDocId < docId)
            relDocId = relations.advance(docId);
        if (relDocId == docId) {
            if (relations.startPosition() < start)
                relations.advanceStartPosition(start);
            while (relations.startPosition() < end) {
                RelationInfo relationInfo = relations.getRelationInfo();
                // Capture relation if:
                // - start is within the to-capture span, and
                // - end is within the to-capture span, OR
                //   the relation is a cross-field relation (i.e. parallel corpus)
                //   (in that case, target is in a different field, so the to-capture span doesn't apply)
                if (relationInfo.isCrossFieldRelation() || relations.endPosition() <= end) {
                    capturedRelations.add(relationInfo.copy());
                }
                relations.nextStartPosition();
            }
        }
        capturedRelations.sort(RelationInfo::compareTo);
    }

    @Override
    public String toString() {
        return "RCAP(" + in + ", " + relations + ", " + toCapture + ", " + captureAs + ")";
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        relations.setHitQueryContext(context);
        this.context = context;
        if (!StringUtils.isEmpty(toCapture))
            this.toCaptureIndex = context.registerMatchInfo(toCapture, null);
        this.captureAsIndex = context.registerMatchInfo(captureAs, MatchInfo.Type.LIST_OF_RELATIONS);
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        // We've already gathered matchInfo in accept(); just copy it over
        MatchInfo.mergeInto(matchInfo, this.matchInfo);
    }

    @Override
    public boolean hasMatchInfo() {
        return true;
    }
}
