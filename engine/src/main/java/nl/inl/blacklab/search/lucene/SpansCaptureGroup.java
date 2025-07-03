package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.FilterSpans;

/**
 * Captures its clause as a captured group.
 *
 * Registers itself with the HitQueryContext so others can access its start()
 * and end() when they want to.
 */
class SpansCaptureGroup extends BLFilterSpans<BLSpans> {

    /** group name */
    private final String name;

    /**
     * group index (where in the Spans[] to place our start/end position in
     * getCapturedGroups())
     */
    private int groupIndex;

    /**
     * How to adjust the left edge of the captured hits while matching. (necessary
     * because we try to internalize constant-length neighbouring clauses into our
     * clause to speed up matching)
     */
    final int leftAdjust;

    /**
     * How to adjust the right edge of the captured hits while matching. (necessary
     * because we try to internalize constant-length neighbouring clauses into our
     * clause to speed up matching)
     */
    private final int rightAdjust;

    /** If set: capture as type TAG, with this tag name.
     *  Note that this only exists to support the legacy external index format.
     *  For the integrated format, tag capturing is handled by SpansRelations directly. */
    private final String tagName;

    /**
     * Constructs a SpansCaptureGroup.
     *
     * @param clause the clause to capture
     * @param name group name
     * @param leftAdjust how to adjust the captured group's start position
     * @param rightAdjust how to adjust the captured group's end position
     * @param tagName if set: capture as type TAG, with this tag name (old external index only)
     */
    public SpansCaptureGroup(BLSpans clause, String name, int leftAdjust, int rightAdjust, String tagName) {
        super(clause);
        this.name = name;
        this.leftAdjust = leftAdjust;
        this.rightAdjust = rightAdjust;
        this.tagName = tagName;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        return FilterSpans.AcceptStatus.YES;
    }

    @Override
    public String toString() {
        String adj = (leftAdjust != 0 || rightAdjust != 0 ? ", " + leftAdjust + ", " + rightAdjust : "");
        return "CAPTURE(" + in + ", " + name + adj + ")";
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        this.groupIndex = context.registerMatchInfo(name, MatchInfo.Type.SPAN);
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        super.getMatchInfo(matchInfo);
        // Place our start and end position at the correct index in the array
        int start = startPosition() + leftAdjust;
        int end = endPosition() + rightAdjust;
        matchInfo[groupIndex] = tagName == null ? SpanInfo.create(start, end, getOverriddenField()) :
            RelationInfo.createTag(start, end, tagName, getOverriddenField());
    }

    @Override
    public boolean hasMatchInfo() {
        return true;
    }
}
