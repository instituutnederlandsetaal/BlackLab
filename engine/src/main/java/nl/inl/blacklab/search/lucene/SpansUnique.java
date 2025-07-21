package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.queries.spans.FilterSpans;

/**
 * Remove consecutive duplicate hits from a source spans.
 */
class SpansUnique extends BLFilterSpans<BLSpans> {

    /** Document id for the previous match */
    int prevDoc = -1;

    /** Start point for the previous match */
    int prevStart = -1;

    /** End point for the previous match */
    int prevEnd = -1;

    /** Match info for the previous match */
    MatchInfo[] prevMatchInfo = null;

    /** Match info for the previous match */
    MatchInfo[] curMatchInfo = null;

    HitQueryContext context;

    /**
     * Construct SpansUnique.
     *
     * @param in (start- or endpoint-sorted) Spans to make unique
     */
    public SpansUnique(BLSpans in) {
        super(in, new SpanGuaranteesAdapter(in.guarantees()) {
            @Override
            public boolean hitsHaveUniqueStartEndAndInfo() {
                return true;
            }

            @Override
            public boolean hitsHaveUniqueStartEnd() {
                return !in.hasMatchInfo();
            }
        });
        // Validate clause guarantees
        if (! (in.guarantees().hitsStartPointSorted() || in.guarantees().hitsEndPointSorted()))
            throw new IllegalArgumentException("Clause must be either start- or endpoint sorted");
        if (in.guarantees().hitsHaveUniqueStartEndAndInfo())
            throw new IllegalArgumentException("Clause already has unique hits");
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        this.context = context;
    }

    @Override
    public String toString() {
        return "UNIQUE(" + in + ")";
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        if (childClausesCaptureMatchInfo) {
            // Make sure we have match info for this candidate, to compare to previous and next hit
            if (curMatchInfo == null)
                curMatchInfo = new MatchInfo[context == null ? 0 : context.numberOfMatchInfos()];
            else
                Arrays.fill(curMatchInfo, null);
            candidate.getMatchInfo(curMatchInfo);
        }
        if (candidate.docID() == prevDoc && candidate.startPosition() == prevStart && candidate.endPosition() == prevEnd) {
            // Seems like a dupe, but we may have to check match info as well
            boolean isDupe = true;
            if (childClausesCaptureMatchInfo) {
                // Check match info as well
                isDupe = prevMatchInfo != null && Arrays.equals(prevMatchInfo, curMatchInfo);
            }
            if (isDupe) {
                // Not unique doc/start/end/match info
                return FilterSpans.AcceptStatus.NO;
            }
        }
        // Unique hit. Remember for next time.
        prevDoc = candidate.docID();
        prevStart = candidate.startPosition();
        prevEnd = candidate.endPosition();
        if (childClausesCaptureMatchInfo) {
            // Remember previous match info
            if (prevMatchInfo == null)
                prevMatchInfo = new MatchInfo[context == null ? 0 : context.numberOfMatchInfos()];
            else
                Arrays.fill(prevMatchInfo, null);
            System.arraycopy(curMatchInfo, 0, prevMatchInfo, 0, prevMatchInfo.length);
        }
        return FilterSpans.AcceptStatus.YES;
    }

}
