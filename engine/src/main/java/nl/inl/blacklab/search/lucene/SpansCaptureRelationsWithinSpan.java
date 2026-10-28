package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queries.spans.FilterSpans;

/**
 * Captures all relations within a match info span.
 */
class SpansCaptureRelationsWithinSpan extends BLFilterSpans<BLSpans> {

    static class RelationsCapturer {
        private final BLSpans relations;

        /** Relations already read in this document, available for reuse by overlapping captures. */
        private final List<RelationInfo> bufferedRelations = new ArrayList<>();

        RelationsCapturer(BLSpans relations) {
            this.relations = relations;
        }

        void captureRelationsWithinSpan(int docId, int start, int end, List<RelationInfo> capturedRelations)
                throws IOException {
            capturedRelations.clear();
            int relDocId = relations.docID();
            if (relDocId < docId) {
                bufferedRelations.clear();
                relDocId = relations.advance(docId);
            }
            if (relDocId != docId)
                return;

            if (relations.startPosition() < 0)
                relations.nextStartPosition();
            while (relations.startPosition() < end) {
                bufferedRelations.add(relations.getRelationInfo().copy());
                relations.nextStartPosition();
            }

            // Unlike hits from BLSpans, named capture spans (is SpansCaptureRelationsWithinSpan.toCapture is set) need
            // not arrive in start-position order. So the value of start may be less than the start position of the
            // last hit this method was called with. This means we have to keep the all the relations we've buffered so
            // far, and binary search for relations that starts at or after the start position of the current hit.
            // TODO: if SpansCaptureRelationsWithinSpan.toCapture == null, we know the hits arrive in start-position
            //       order, so we can remove any relations that end before the start position of the current hit from
            //       bufferedRelations, and avoid the binary search.
            int min = 0, max = bufferedRelations.size();
            while (min < max) {
                int mid = (min + max) >>> 1;
                if (bufferedRelations.get(mid).getSpanStart() < start)
                    min = mid + 1;
                else
                    max = mid;
            }
            // Now find all the buffered relations that overlap with the current hit, and add them to capturedRelations.
            for (int i = min; i < bufferedRelations.size(); i++) {
                RelationInfo relation = bufferedRelations.get(i);
                // Capture relation if:
                // - start is within the to-capture span, and
                // - end is within the to-capture span
                // (note that getSpanStart|End return the start/end of the source for cross-field relations, which is
                //  what we want: we want to ignore target in that case because it's in a different field)
                if (relation.getSpanStart() >= end)
                    break; // relations are sorted by start position, so no more can overlap
                if (relation.getSpanEnd() <= end)
                    capturedRelations.add(relation);
            }
            capturedRelations.sort(RelationInfo::compareTo);
        }

        void setSpansContext(SpansContext context) {
            relations.setSpansContext(context);
        }

        @Override
        public String toString() {
            return relations.toString();
        }
    }

    private final RelationsCapturer relations;

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
    private SpansContext context;

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
     * @param toCapture name of the match info span to capture relations from, or null to capture all relations in hit
     * @param captureAs name to capture the list of relations as
     */
    public SpansCaptureRelationsWithinSpan(BLSpans clause, BLSpans relations, String toCapture, String captureAs) {
        super(clause);
        this.relations = new RelationsCapturer(relations);
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
            relations.captureRelationsWithinSpan(candidate.docID(), start, end, capturedRelations);
            matchInfo[captureAsIndex] = RelationListInfo.create(capturedRelations, getOverriddenField());
        }

        return FilterSpans.AcceptStatus.YES;
    }

    @Override
    public String toString() {
        return "RCAP(" + in + ", " + relations + ", " + toCapture + ", " + captureAs + ")";
    }

    @Override
    protected void passSpansContextToClauses(SpansContext context) {
        super.passSpansContextToClauses(context);
        relations.setSpansContext(context);
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
