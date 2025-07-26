package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.lucene.queries.spans.FilterSpans;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * Captures all relations between the hits from two clauses.
 *
 * This is used to capture cross-field (alignment) relations in a parallel corpus.
 *
 * FIXME ? right now, subsequent spans from the source Spans may not overlap!
 *   If they do overlap, some relations may be skipped over.
 *   We should cache (some) relations from the source span so we can be sure we return all
 *   of them, even if the source spans overlap. Use SpansInBuckets or maybe a rewindable
 *   Spans view on top of that class? See @@@ comment below for a possible solution.
 */
class SpansCaptureRelationsBetweenSpans extends BLFilterSpans<BLSpans> {

    public static class Target {

        /** What relations to use to find matches */
        private final SpansBuffered matchRelations;

        /** What relations to capture (usually all of them, not just the ones matched on) */
        private final SpansBuffered captureRelations;

        /** Match info name for the list of captured relations */
        private final String captureRelationsAs;

        /** Group index of captureAs */
        private int captureRelationsIndex = -1;

        /** Span the relation targets must be inside of (or null if there are no hits or we don't care; in the latter
         * case, {@link #hasTargetRestrictions} will be false) */
        private final SpansInBucketsPerDocument target;

        /** If false, there are no target restrictions, so we don't need to check.
         *  (needed because if target == null there might be restrictions, but no hits in current segment) */
        private final boolean hasTargetRestrictions;

        /** If target == null, we may still want to capture the relation targets.
         *  E.g. <code>(...some source query...) ==> A:[]*</code>
         *  In that case, this gives the capture name for that.
         *  (if target is not null, any desired capture operation is included in that,
         *   so we don't need it here) */
        private final List<String> captureTargetAs;

        /** Group index of captureTargetAs */
        private final List<Integer> captureTargetAsIndex = new ArrayList<>();

        /** If target == null and captureTargetAs is set, this gives the target field for capture. */
        private final AnnotatedField targetField;

        /** Should we include the hit on the left side of the relation even if there's no hit on the right side? */
        private final boolean optionalMatch;

        /** Match info index for ==> with-spans(_) capture */
        private int capturedTargetOverlapsIndex = -1;

        /** Used for ==> with-spans(_) capturing */
        private SpansCaptureOverlappingSpans.OverlappingSpansCapturer capturerTargetOverlaps = null;

        /** Name for target spans captured in case of ==> with-spans(_) */
        private String captureTargetOverlapsAs = null;

        public Target(BLSpans matchRelations, BLSpans target, boolean hasTargetRestrictions,
                BLSpans captureRelations, String captureRelationsAs, List<String> captureTargetAs, AnnotatedField targetField,
                boolean optionalMatch, BLSpans captureTargetOverlaps, String captureTargetOverlapsAs) {
            this.matchRelations = new SpansBuffered(matchRelations);
            this.captureRelations = new SpansBuffered(captureRelations);
            this.captureRelationsAs = captureRelationsAs;
            this.target = target == null ? null : new SpansInBucketsPerDocument(target);
            this.hasTargetRestrictions = hasTargetRestrictions;
            this.captureTargetAs = captureTargetAs;
            this.targetField = targetField;
            this.optionalMatch = optionalMatch;
            if (captureTargetOverlaps != null) {
                capturerTargetOverlaps = new SpansCaptureOverlappingSpans.OverlappingSpansCapturer(captureTargetOverlaps);
                this.captureTargetOverlapsAs = captureTargetOverlapsAs;
            }
            assert captureTargetAs != null && !captureTargetAs.isEmpty();
        }

        void setHitQueryContext(HitQueryContext context) {
            matchRelations.setHitQueryContext(context);
            captureRelations.setHitQueryContext(context);
            captureRelationsIndex = context.registerMatchInfo(captureRelationsAs, MatchInfo.Type.LIST_OF_RELATIONS, context.getField(), targetField);

            HitQueryContext targetContext = context.withField(targetField);
            for (String captureName: captureTargetAs) {
                captureTargetAsIndex.add(targetContext.registerMatchInfo(captureName, MatchInfo.Type.SPAN));
            }
            if (target != null)
                target.setHitQueryContext(targetContext);

            if (capturerTargetOverlaps != null) {
                capturerTargetOverlaps.setHitQueryContext(targetContext);
                capturedTargetOverlapsIndex = targetContext.registerMatchInfo(captureTargetOverlapsAs,
                        MatchInfo.Type.LIST_OF_RELATIONS, context.getField(), targetField);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Target target1 = (Target) o;
            return captureRelationsIndex == target1.captureRelationsIndex
                    && hasTargetRestrictions == target1.hasTargetRestrictions
                    && captureTargetAsIndex == target1.captureTargetAsIndex && optionalMatch == target1.optionalMatch
                    && Objects.equals(matchRelations, target1.matchRelations) && Objects.equals(
                    captureRelations, target1.captureRelations) && Objects.equals(captureRelationsAs,
                    target1.captureRelationsAs) && Objects.equals(target, target1.target) && Objects.equals(
                    captureTargetAs, target1.captureTargetAs) && Objects.equals(targetField, target1.targetField);
        }

        @Override
        public int hashCode() {
            return Objects.hash(matchRelations, captureRelations, captureRelationsAs, captureRelationsIndex, target,
                    hasTargetRestrictions, captureTargetAs, captureTargetAsIndex, targetField, optionalMatch);
        }

        @Override
        public String toString() {
            return "Target{" +
                    "matchRelations=" + matchRelations +
                    ", captureRelations=" + captureRelations +
                    ", captureRelationsAs='" + captureRelationsAs + '\'' +
                    ", captureRelationsIndex=" + captureRelationsIndex +
                    ", target=" + target +
                    ", hasTargetRestrictions=" + hasTargetRestrictions +
                    ", captureTargetAs='" + captureTargetAs + '\'' +
                    ", captureTargetAsIndex=" + captureTargetAsIndex +
                    ", targetField='" + targetField + '\'' +
                    ", optionalMatch=" + optionalMatch +
                    '}';
        }
    }

    private final List<Target> targets;

    /** Our hit query context */
    private HitQueryContext context;

    /** Match info for current hit */
    private MatchInfo[] matchInfo;

    /** List of relations used for matching current hit */
    private final List<RelationInfo> matchingRelations = new ArrayList<>();

    /** List of relations captured for current hit */
    private final List<RelationInfo> capturedRelations = new ArrayList<>();

    /** Start of current (source) hit (covers all sources of captured relations) */
    private int adjustedStart;

    /** End of current (source) hit (covers all sources of captured relations) */
    private int adjustedEnd;

    /**
     * Construct a SpansCaptureRelationsWithinSpan.
     *
     * @param source span the relation sources must be inside of
     * @param targets targets of the relations we're capturing
     */
    public SpansCaptureRelationsBetweenSpans(BLSpans source, List<Target> targets) {
        super(source);
        this.targets = targets;
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (super.nextStartPosition() == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;
        return adjustedStart;
    }

    @Override
    public int startPosition() {
        if (atFirstInCurrentDoc)
            return -1;
        if (startPos == -1 || startPos == NO_MORE_POSITIONS)
            return startPos;
        return adjustedStart;
    }

    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1;
        if (startPos == -1 || startPos == NO_MORE_POSITIONS)
            return startPos;
        return adjustedEnd;
    }

    private static class PosMinMax {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        // Prepare matchInfo so we can add captured relations to it
        if (matchInfo == null) {
            matchInfo = new MatchInfo[context.numberOfMatchInfos()];
        } else {
            Arrays.fill(matchInfo, null);
        }
        candidate.getMatchInfo(matchInfo);

        // Find current source span
        int sourceStart = candidate.startPosition();
        int sourceEnd = candidate.endPosition();

        // Our final (source) span will cover all captured relations.
        adjustedStart = sourceStart;
        adjustedEnd = sourceEnd;

        for (Target target: targets) {
            // Capture all relations with source overlapping this span.
            // Also update source start/end and capture relations (we capture more than we match with)
            PosMinMax targetLimits = findRelations(candidate.docID(), target, sourceStart, sourceEnd);
            if (matchingRelations.isEmpty() || // If no relations matched, there is no match.
                    // If there were target restrictions, but no hits (in this index segment), there is no match
                    target.hasTargetRestrictions && target.target == null) {
                if (!target.optionalMatch)
                    return FilterSpans.AcceptStatus.NO;
                continue; // check next target
            }

            // If there's target restrictions, find the target match.
            int targetIndex = -1;
            int targetClauseStart = -1;
            int targetClauseEnd = -1;
            if (target.hasTargetRestrictions) {
                // There are target restrictions.
                // Find the smallest target span that overlaps the highest number of the relations we just matched.
                // First, put the target spans in the right document.
                int targetDocId = target.target.docID();
                if (targetDocId < candidate.docID()) {
                    targetDocId = target.target.advance(candidate.docID());
                    target.target.nextBucket();
                }
                if (targetDocId != candidate.docID()) {
                    // Target document has no matches. Reject this hit.
                    if (!target.optionalMatch)
                        return FilterSpans.AcceptStatus.NO;
                    continue; // check next target
                }
                // Target positioned in doc. Find best matching hit.
                int targetSpanLength = Integer.MAX_VALUE;
                int targetRelationsCovered = 0;
                for (int i = 0; i < target.target.bucketSize(); i++) {
                    // Check if this is a better target match than we had before.
                    int targetStart = target.target.startPosition(i);
                    int targetEnd = target.target.endPosition(i);
                    if (targetLimits.min >= targetEnd || targetLimits.max <= targetStart) {
                        // The targets of the relations we matched on are outside this target span. Reject it.
                        continue;
                    }
                    // There is some overlap between the target span and the relations we matched on.
                    // Find out which relations overlap this target span, so we can pick the best target span.
                    int relationsCovered = (int) matchingRelations.stream()
                            .filter(r -> r.getTargetEnd() > targetStart && r.getTargetStart() < targetEnd)
                            .count();
                    int length = targetEnd - targetStart;
                    if (relationsCovered > targetRelationsCovered
                            || relationsCovered == targetRelationsCovered && length < targetSpanLength) {
                        targetIndex = i;
                        targetSpanLength = length;
                        targetRelationsCovered = relationsCovered;
                    }
                }
                if (targetRelationsCovered == 0) {
                    // A valid hit must have at least one matching relation in each target.
                    if (!target.optionalMatch)
                        return FilterSpans.AcceptStatus.NO;
                    else
                        continue; // check next target
                }

                // Only keep the captured relations that overlap the target span we found.
                final int tcs = targetClauseStart = target.target.startPosition(targetIndex);
                final int tce = targetClauseEnd = target.target.endPosition(targetIndex);
                capturedRelations.removeIf(r -> r.getTargetEnd() <= tcs || r.getTargetStart() >= tce);
            }

            capturedRelations.sort(RelationInfo::compareTo);

            matchInfo[target.captureRelationsIndex] = RelationListInfo.create(capturedRelations, getOverriddenField());

            int targetStart = targetLimits.min;
            int targetEnd = targetLimits.max;
            if (target.hasTargetRestrictions) {
                // Expand target to cover all relations matched by =type=> operator,
                // so e.g. "water" =sentence-alignment=>en "water" will return whole sentences for target,
                // not just the matching words from the query.
                if (targetClauseStart < targetStart)
                    targetStart = targetClauseStart;
                if (targetClauseEnd > targetEnd)
                    targetEnd = targetClauseEnd;
            }

            // Capture target span
            // (may be captured multiple times, one implicitly with __@target at the end to determine the "foreign hit"
            // later, and once explicitly specified by the user in the query, e.g. ==> A:"something")
            for (int index: target.captureTargetAsIndex) {
                matchInfo[index] = SpanInfo.create(targetStart, targetEnd, target.targetField);
            }

            if (target.hasTargetRestrictions) {
                // Get captures from the target match
                target.target.getMatchInfo(targetIndex, matchInfo);
            } else {
                // If target was with-spans([]+) or similar, we should capture the spans covering the target
                // we found now (because we obviously didn't actually find all []+ hits with all their spans,
                // we took the shortcut of just looking at the targets of the matching relations,
                // so finding the spans overlapping the target hasn't been done yet)
                if (target.capturerTargetOverlaps != null) {
                    List<RelationInfo> capturedSpans = target.capturerTargetOverlaps.processHit(candidate.docID(), targetStart, targetEnd);
                    matchInfo[target.capturedTargetOverlapsIndex] = RelationListInfo.create(capturedSpans, getOverriddenField());
                }
            }
        }

        return FilterSpans.AcceptStatus.YES;
    }

    private PosMinMax findRelations(int docId, Target target, int sourceStart, int sourceEnd)
            throws IOException {
        // Capture all relations with source overlapping this span.
        PosMinMax targetLimits = findMatchingRelations(matchingRelations, docId, target.matchRelations,
                sourceStart, sourceEnd);
        if (!matchingRelations.isEmpty()) {
            // update source start/end to cover all matching relations
            // Our final (source) span will cover all captured relations, so that
            // e.g. "the" =sentence-alignment=>nl "de" will have the aligned sentences as hits, not just single words.
            for (RelationInfo r: matchingRelations) {
                if (r.getSourceStart() < adjustedStart)
                    adjustedStart = r.getSourceStart();
                if (r.getSourceEnd() > adjustedEnd)
                    adjustedEnd = r.getSourceEnd();
            }

            // Find relations to capture.
            // (these may be more than just the relations we matched on, e.g. if we match by
            //  sentence alignment, we still want to see any word alignments returned in the result)
            findMatchingRelations(capturedRelations, docId, target.captureRelations, adjustedStart,
                    adjustedEnd);
        }
        return targetLimits;
    }

    private PosMinMax findMatchingRelations(List<RelationInfo> results, int targetDocId, SpansBuffered relations, int sourceStart, int sourceEnd) throws IOException {
        results.clear();
        PosMinMax targetPos = new PosMinMax();
        int docId = relations.docID();
        if (docId < targetDocId)
            docId = relations.advance(targetDocId);
        if (docId == targetDocId) {

            // Relations may have been advanced beyond our start position. If so, reset it
            // back to the start of the previous hit, which we marked.
            if (relations.startPosition() > sourceStart)
                relations.reset(); // rewind to last mark (start of previous source hit)

            // Advance relations such that the relation source end position is after the
            // current start position (of the query source), i.e. they may overlap.
            while (relations.endPosition() <= sourceStart) {
                if (relations.nextStartPosition() == NO_MORE_POSITIONS)
                    break;
            }

            // Mark the current relations position so we can rewind to it for the next hit if necessary.
            relations.mark();

            while (relations.startPosition() < sourceEnd) {
                if (relations.endPosition() > sourceStart) {
                    // Source of this relation overlaps our source hit.
                    RelationInfo relInfo = relations.getRelationInfo().copy();
                    results.add(relInfo);
                    // Keep track of the min and max target positions so we can quickly reject targets below.
                    targetPos.min = Math.min(targetPos.min, relInfo.getTargetStart());
                    targetPos.max = Math.max(targetPos.max, relInfo.getTargetEnd());
                }

                relations.nextStartPosition();
            }
        }
        return targetPos;
    }

    @Override
    public String toString() {
        return "==>(" + in + ", " + targets + ")";
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        this.context = context;
        for (Target target: targets)
            target.setHitQueryContext(context);
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
