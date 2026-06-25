package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.queries.spans.FilterSpans;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * Adjust relations spans to match source, target, or entire relation.
 */
class SpansRelationSpanAdjust extends BLFilterSpans<BLSpans> {

    /** how to adjust spans (if adjustToCapture != null) */
    private final RelationInfo.SpanMode spanMode;

    /** if non-null, adjust to this capture (instead of relation span mode) */
    private final String adjustToCapture;

    /** if adjustToCapture != null, the match info index we're capturing */
    private int captureIndex;

    /** Adjusted start position of current hit */
    private int startAdjusted = -1;

    /** Adjusted end position of current hit */
    private int endAdjusted = -1;

    private HitQueryContext context;

    private MatchInfo[] matchInfo;

    /** What field is our clause in? */
    private final AnnotatedField clauseField;

    /** Whether or not the current hit should be included. */
    private FilterSpans.AcceptStatus filterResult = FilterSpans.AcceptStatus.YES;

    /**
     * Constructs a SpansRelationSpanAdjust.
     *
     * @param in       spans to adjust
     * @param spanMode how to adjust spans
     */
    public SpansRelationSpanAdjust(BLSpans in, AnnotatedField clauseField, RelationInfo.SpanMode spanMode,
            String adjustToCapture) {
        super(in, adjustToCapture != null ? SpanGuarantees.NONE : SpanQueryRelationSpanAdjust.createGuarantees(in.guarantees(), spanMode));
        this.clauseField = clauseField;
        this.spanMode = spanMode;
        this.adjustToCapture = adjustToCapture;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        if (spanMode == RelationInfo.SpanMode.SOURCE && in.getRelationInfo().isRoot()) {
            // Need source, but this has no source
            return FilterSpans.AcceptStatus.NO;
        }
        setAdjustedStartEnd();
        return filterResult;
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        startAdjusted = endAdjusted = -1;
        return super.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        startAdjusted = endAdjusted = -1;
        return super.advance(target);
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        assert positionedInDoc();
        startAdjusted = endAdjusted = -1;
        return super.twoPhaseCurrentDocMatches();
    }

    @Override
    public int startPosition() {
        return atFirstInCurrentDoc ? -1 : startAdjusted;
    }

    @Override
    public int endPosition() {
        return atFirstInCurrentDoc ? -1 : endAdjusted;
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        super.nextStartPosition();
        setAdjustedStartEnd();
        return startAdjusted;
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        this.context = context;
        super.passHitQueryContextToClauses(context.withField(clauseField));
        this.captureIndex = adjustToCapture == null ? -1 : context.getMatchInfoDefs().indexOf(adjustToCapture);
    }

    private void setAdjustedStartEnd() {
        filterResult = FilterSpans.AcceptStatus.YES;
        if (startPos == NO_MORE_POSITIONS) {
            startAdjusted = endAdjusted = NO_MORE_POSITIONS;
        } else if (atFirstInCurrentDoc || startPos < 0) {
            startAdjusted = endAdjusted = -1;
        } else {
            boolean needMatchInfo = spanMode == RelationInfo.SpanMode.ALL_SPANS || adjustToCapture != null;
            if (needMatchInfo) {
                // We need all match info because we want to expand the current span to include all matched relations,
                // or adjust the current span to be one of the captures
                if (matchInfo == null)
                    matchInfo = new MatchInfo[context.numberOfMatchInfos()];
                else
                    Arrays.fill(matchInfo, null);
                in.getMatchInfo(matchInfo);
            }
            if (adjustToCapture != null) {
                // We want to adjust the current span to one of the captures.
                MatchInfo info = matchInfo[captureIndex];
                if (info != null) {
                    startAdjusted = info.getSpanStart();
                    endAdjusted = info.getSpanEnd();
                } else {
                    // Nothing captured for this hit; skip
                    filterResult = FilterSpans.AcceptStatus.NO;
                }
            } else if (spanMode == RelationInfo.SpanMode.ALL_SPANS) {
                startAdjusted = in.startPosition();
                endAdjusted = in.endPosition();
                for (MatchInfo info: matchInfo) {
                    if (info != null && info.getType() == MatchInfo.Type.RELATION) {

                        // skip relations to other fields (parallel corpora)
                        RelationInfo rel = (RelationInfo) info;
                        if (rel.isCrossFieldRelation())
                            continue;

                        // This is a relations match. Take this into account for the full span.
                        // (capture groups are not taken into account, but should already fall into the span anyway)
                        if (info.getSpanStart() < startAdjusted)
                            startAdjusted = info.getSpanStart();
                        if (info.getSpanEnd() > endAdjusted)
                            endAdjusted = info.getSpanEnd();
                    }
                }
            } else {
                RelationInfo relationInfo = in.getRelationInfo();
                if (relationInfo == null) {
                    // No relation info available; use the original span
                    startAdjusted = in.startPosition();
                    endAdjusted = in.endPosition();
                } else {
                    startAdjusted = relationInfo.spanStart(spanMode);
                    endAdjusted = relationInfo.spanEnd(spanMode);
                }
            }
        }
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        if (this.matchInfo != null) {
            // We've already retrieved our clause's match info. Use that.
            MatchInfo.mergeInto(matchInfo, this.matchInfo);
        } else {
            super.getMatchInfo(matchInfo);
        }
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        assert target > startPosition();
        if (atFirstInCurrentDoc) {
            int startPos = nextStartPosition();
            if (startPos >= target)
                return startPos;
        }
        if (spanMode != RelationInfo.SpanMode.FULL_SPAN) {
            // We can't skip because the spans we produce are not guaranteed to be sorted by start position.
            // Call the naive implementation.
            if (BLSpans.naiveAdvanceStartPosition(this, target) == NO_MORE_POSITIONS) {
                startPos = startAdjusted = endAdjusted = NO_MORE_POSITIONS;
            } else {
                setAdjustedStartEnd();
            }
        } else {
            // We know our spans will be in order, so we can use the more efficient advanceStartPosition()
            if (in.advanceStartPosition(target) == NO_MORE_POSITIONS) {
                startPos = startAdjusted = endAdjusted = NO_MORE_POSITIONS;
            } else {
                setAdjustedStartEnd();
            }
        }
        return startAdjusted;
    }

    @Override
    public String toString() {
        String name = (spanMode == null ? "CSPAN" : "RSPAN");
        return name + "(" + in + ", " + (spanMode == null ? adjustToCapture : spanMode) + ")";
    }

}
