package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.queries.spans.FilterSpans;

/**
 * Captures all spans that enclose each hit.
 */
class SpansCaptureOverlappingSpans extends BLFilterSpans<BLSpans> {

    /** Match info name for the list of captured spans */
    final String captureAs;

    /** Group index of captureAs */
    private int captureAsIndex = -1;

    /** Our hit query context */
    private HitQueryContext context;

    /**
     * Match info for current hit
     */
    MatchInfo[] matchInfo;

    /**
     * Can capture spans (hits from a BLSpans) that overlap
     * with each hits we encounter.
     *
     * Separate class because we need to use it in the situation
     * _ ==> with-spans(_), where we have no target restrictions
     * (so no BLSpans generating target hits) but still want to capture
     * spans when we determine the target hits based on the relations matched.
     * See SpansCaptureRelationsBetweenSpans.
     */
    static class OverlappingSpansCapturer {

        /** The spans we potentially want to capture */
        private final BLSpans spansToCapture;

        /** List of relations captured for current hit */
        private final List<RelationInfo> capturedSpans = new ArrayList<>();

        /**
         * All spans that start before the current hit's start position and close
         * after its START position. We still have to check if they close after its END position.
         * We update this list as we process hits, removing spans that can no longer overlap this
         * or future hits, and adding new ones that may overlap this or future hits.
         */
        private final List<RelationInfo> possiblyOverlappingSpans = new ArrayList<>();

        public OverlappingSpansCapturer(BLSpans spansToCapture) {
            this.spansToCapture = spansToCapture;
        }

        /**
         * Process a hit from the clause.
         *
         * Note that hits must be processed in order of increasing docId and start position.
         *
         * @param docId document id of the hit
         * @param start start position of the hit
         * @param end end position of the hit
         * @return list of captured relations
         */
        List<RelationInfo> processHit(int docId, int start, int end) throws IOException {
            // Put spansToCapture in same document as hit
            int spansDocId = spansToCapture.docID();
            if (spansDocId < docId) {
                possiblyOverlappingSpans.clear();
                spansDocId = spansToCapture.advance(docId);
                if (spansDocId != NO_MORE_DOCS)
                    spansToCapture.nextStartPosition(); // position at first span
            }
            if (spansDocId == docId) {
                // Remove all spans that close before the current hit's start position
                possiblyOverlappingSpans.removeIf(span -> span.getSpanEnd() <= start);

                // Find and add new spans that open before the current hit's start position and close after it
                while (spansToCapture.startPosition() != NO_MORE_POSITIONS) {
                    int spanStart = spansToCapture.startPosition();
                    if (spanStart >= end) {
                        // We don't need to look any further for this hit.
                        break;
                    }
                    if (spanStart >= 0 && spansToCapture.endPosition() > start) {
                        // This span may overlap current and following hits. Remember it.
                        possiblyOverlappingSpans.add(spansToCapture.getRelationInfo().copy());
                    }
                    spansToCapture.nextStartPosition();
                }
            }

            // Capture all relations within the toCapture span
            capturedSpans.clear();
            possiblyOverlappingSpans.forEach(span -> {
                boolean b = span.getSpanStart() < end && span.getSpanEnd() > start;
                if (b) {
                    // Actually overlaps this hit, record
                    capturedSpans.add(span);
                }
            });
            capturedSpans.sort(RelationInfo::compareTo);
            return capturedSpans;
        }

        public void setHitQueryContext(HitQueryContext context) {
            spansToCapture.setHitQueryContext(context);
        }

        @Override
        public String toString() {
            return spansToCapture.toString();
        }
    }

    /** Can capture spans as we process hits */
    OverlappingSpansCapturer capturer;

    /**
     * Capture spans overlapping each hit from clause.
     *
     * @param clause clause we're capturing from
     * @param spans spans to capture
     * @param captureAs name to capture the list of relations as
     */
    public SpansCaptureOverlappingSpans(BLSpans clause, BLSpans spans, String captureAs) {
        super(clause);
        this.capturer = new OverlappingSpansCapturer(spans);
        this.captureAs = captureAs;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        assert captureAsIndex >= 0 : "Negative captureAs index";
        int start = candidate.startPosition();
        int end = candidate.endPosition();
        assert start >= 0;

        List<RelationInfo> captured = capturer.processHit(candidate.docID(), start, end);

        // We can "only" get match info for our own clause, but that should be enough
        // (we can only capture spans from match info captured within own clause)
        if (matchInfo == null)
            matchInfo = new MatchInfo[context.numberOfMatchInfos()];
        else
            Arrays.fill(matchInfo, null);
        candidate.getMatchInfo(matchInfo);
        matchInfo[captureAsIndex] = RelationListInfo.create(captured, getOverriddenField());

        return FilterSpans.AcceptStatus.YES;
    }

    @Override
    public String toString() {
        return "with-spans(" + in + ", " + capturer + ", " + captureAs + ")";
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        capturer.setHitQueryContext(context);
        this.context = context;
        this.captureAsIndex = context.registerMatchInfo(captureAs, MatchInfo.Type.LIST_OF_RELATIONS);
        assert captureAsIndex >= 0 : "Negative captureAs index";
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
