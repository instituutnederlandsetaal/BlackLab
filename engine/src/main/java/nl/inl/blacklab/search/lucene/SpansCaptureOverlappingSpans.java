package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.search.spans.FilterSpans;

/**
 * Captures all spans that enclose each hit.
 */
class SpansCaptureOverlappingSpans extends BLFilterSpans<BLSpans> {

    private final BLSpans spans;

    /** Match info name for the list of captured spans */
    final String captureAs;

    /** Group index of captureAs */
    private int captureAsIndex = -1;

    /** Our hit query context */
    private HitQueryContext context;

    /** Match info for current hit */
    private MatchInfo[] matchInfo;

    /** List of relations captured for current hit */
    private List<RelationInfo> capturedSpans = new ArrayList<>();

    /** All spans that start before the current hit's start position and close
     *  after its START position. We still have to check if they close after its END position. */
    private List<RelationInfo> possiblyOverlappingSpans = new ArrayList<>();

    /**
     * Capture spans overlapping each hit from clause.
     *
     * @param clause clause we're capturing from
     * @param spans spans to capture
     * @param captureAs name to capture the list of relations as
     */
    public SpansCaptureOverlappingSpans(BLSpans clause, BLSpans spans, String captureAs) {
        super(clause);
        this.spans = spans;
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
        // (we can only capture spans from match info captured within own clause)
        candidate.getMatchInfo(matchInfo);
        int start = in.startPosition();
        int end = in.endPosition();

        // Put spans in our document
        int docId = spans.docID();
        if (docId < candidate.docID()) {
            possiblyOverlappingSpans.clear();
            docId = spans.advance(candidate.docID());
            if (docId != NO_MORE_DOCS)
                spans.nextStartPosition(); // position at first span
        }
        if (docId == candidate.docID()) {
            // Remove all spans that close before the current hit's start position
            possiblyOverlappingSpans.removeIf(span -> span.getSpanEnd() <= start);

            // Find and add new spans that open before the current hit's start position and close after it
            while (spans.startPosition() != NO_MORE_POSITIONS) {
                int spanStart = spans.startPosition();
                if (spanStart >= end) {
                    // We don't need to look any further for this hit.
                    break;
                }
                if (spanStart >= 0 && spans.endPosition() > start) {
                    // This span may overlap current and following hits. Remember it.
                    possiblyOverlappingSpans.add(spans.getRelationInfo().copy());
                }
                spans.nextStartPosition();
            }
        }

        if (start >= 0) {
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
            matchInfo[captureAsIndex] = RelationListInfo.create(capturedSpans, getOverriddenField());
        }
        return FilterSpans.AcceptStatus.YES;
    }

    @Override
    public String toString() {
        return "with-spans(" + in + ", " + spans + ", " + captureAs + ")";
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        spans.setHitQueryContext(context);
        this.context = context;
        this.captureAsIndex = context.registerMatchInfo(captureAs, MatchInfo.Type.LIST_OF_RELATIONS);
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        // We've already gathered matchInfo in accept(); just copy it over
        int n = Math.min(matchInfo.length, this.matchInfo.length);
        for (int i = 0; i < n; i++) {
            if (this.matchInfo[i] != null)
                matchInfo[i] = this.matchInfo[i];
        }
    }

    @Override
    public boolean hasMatchInfo() {
        return true;
    }
}
