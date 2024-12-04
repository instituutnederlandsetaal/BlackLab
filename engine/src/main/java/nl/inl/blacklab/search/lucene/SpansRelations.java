package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Iterator;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.payloads.PayloadSpanCollector;
import org.apache.lucene.search.spans.FilterSpans;
import org.apache.lucene.store.ByteArrayDataInput;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.RelationInfoSegmentReader;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.SpanQueryRelations.Direction;

/**
 * Gets spans for relations matches.
 *
 * Relations are indexed in the _relation annotation, with information in the
 * payload to determine the source and target of the relation. The source and
 * target also define the span that is returned.
 */
class SpansRelations extends BLFilterSpans<BLSpans> {

    /** Empty payload (actually returns as if there is no payload, so we need our own empty array value) */
    protected static final byte[] EMPTY_PAYLOAD = new byte[0];

    private final int NOT_YET_NEXTED = -1;

    /** Source and target for this relation */
    private RelationInfo relationInfo = null;

    /** Have we fetched relation info (decoded payload) for current hit yet? */
    private boolean fetchedRelationInfo = false;

    /** If true, we have to skip the primary value indicator in the payload (see PayloadUtils) */
    private final boolean payloadIndicatesPrimaryValues;

    /** Filter to apply to the relations */
    private final Direction direction;

    /** What span to return for the relations found */
    private final RelationInfo.SpanMode spanMode;

    /** Group number where we'll capture our relation info */
    private int groupIndex;

    /** Relation type we're looking for */
    private final String relationType;

    /** Name to capture the relation info as */
    private final String captureAs;

    /** For looking up attribute values in the relation index */
    private final RelationInfoSegmentReader relInfo;

    /**
     * Can our spans include root relations, or are we sure it doesn't?
     * If it might include root relations, we need to check for this in accept(),
     * forcing us to decode the payload.
     */
    private final boolean spansMayIncludeRoots = true;

    private final String sourceField;

    /**
     * Construct SpansRelations.
     * NOTE: relation payloads contain the location of the other side of the relation. To work with these,
     * we also need to know if there's "is primary value" indicators in (some of) the payloads,
     * so we can skip these. See {@link PayloadUtils}.
     *
     * @param sourceField                   name of the annotated field that is the source field of the relations
     * @param relationType                  type of relation we're looking for
     * @param relationsMatches              relation matches for us to decode
     * @param payloadIndicatesPrimaryValues whether or not there's "is primary value" indicators in the payloads
     * @param direction                     direction of the relation
     * @param spanMode                      what span to return for the relations found
     * @param captureAs                     name to capture the relation info as, or empty not to capture
     */
    public SpansRelations(String sourceField, String relationType, BLSpans relationsMatches,
            boolean payloadIndicatesPrimaryValues, Direction direction, RelationInfo.SpanMode spanMode,
            String captureAs, RelationInfoSegmentReader relInfo) {
        super(relationsMatches,
                SpanQueryRelations.createGuarantees(relationsMatches.guarantees(), direction, spanMode));
        this.sourceField = sourceField;
        this.relationType = relationType;
        this.payloadIndicatesPrimaryValues = payloadIndicatesPrimaryValues;
        this.direction = direction;
        this.spanMode = spanMode;
        this.captureAs = captureAs == null ? "" : captureAs;
        this.relInfo = relInfo;
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        // Find the target field from the relation class (e.g. for class al__de, target field will be something like
        // contents__de)
        // Be careful not to interpret special relations class __tag as a parallel field!
        String relClass = RelationUtil.classFromFullType(relationType);
        boolean isInlineTag = relClass.equals(RelationUtil.CLASS_INLINE_TAG);
        String version = isInlineTag ? "" : AnnotatedFieldNameUtil.versionFromParallelFieldName(relClass);
        String targetField = StringUtils.isEmpty(version) ? sourceField :
                AnnotatedFieldNameUtil.changeParallelFieldVersion(context.getDefaultField(), version);

        // When capturing relations, remember that we're producing hits in a different field.
        // (used with parallel corpora)
        relationInfo = RelationInfo.createWithFields(sourceField, targetField);
        // Register our group
        if (!captureAs.isEmpty()) {
            MatchInfo.Type type = isInlineTag ? MatchInfo.Type.INLINE_TAG : MatchInfo.Type.RELATION;
            this.groupIndex = context.registerMatchInfo(captureAs, type, sourceField, targetField);
        }
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        assert positionedAtHit();
        if (!captureAs.isEmpty())
            matchInfo[groupIndex] = getRelationInfo().copy();
    }

    @Override
    public boolean hasMatchInfo() {
        return !captureAs.isEmpty() || in.hasMatchInfo();
    }

    /**
     * Return current relation info.
     *
     * @return current relation info object; don't store or modify this, use .copy() first!
     */
    public RelationInfo getRelationInfo() {
        // Decode the payload if we haven't already
        if (!fetchedRelationInfo) {
            collector.reset();
            // NOTE: relationsMatches is from a BLSpanTermQuery, a leaf, so we know there can only be one payload
            //   each relation gets a payload, so there should always be one
            try {
                in.collect(collector);
                Iterator<byte[]> iterator = collector.getPayloads().iterator();
                byte[] payload = iterator.hasNext() ? iterator.next() : EMPTY_PAYLOAD;
                ByteArrayDataInput dataInput = PayloadUtils.getDataInput(payload, payloadIndicatesPrimaryValues);
                if (relationInfo == null) { // should only happen in tests
                    relationInfo = RelationInfo.create();
                }
                relationInfo.deserialize(in.startPosition(), dataInput);
            } catch (IOException e) {
                throw new BlackLabRuntimeException("Error getting payload");
            }
            if (collector.term != null) // can happen during testing...
                relationInfo.setIndexedTerm(collector.term.text(), docID(), relInfo);
            fetchedRelationInfo = true;
        }
        return relationInfo;
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        startPos = NOT_YET_NEXTED;
        return super.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        startPos = NOT_YET_NEXTED;
        return super.advance(target);
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            return startPos;
        }
        while (true) {
            if (in.nextStartPosition() == NO_MORE_POSITIONS) {
                startPos = NO_MORE_POSITIONS;
                return startPos;
            }
            switch (accept(in)) {
            case YES:
                return startPos;
            case NO:
                continue;
            case NO_MORE_IN_CURRENT_DOC:
                startPos = NO_MORE_POSITIONS;
                return startPos;
            }
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
        if (direction == Direction.FORWARD && spanMode == RelationInfo.SpanMode.FULL_SPAN
                || spanMode == RelationInfo.SpanMode.SOURCE) {
            // We know our spans will be in order, so we can use the more efficient advanceStartPosition()
            return super.advanceStartPosition(target);
        }
        // Our spans may not be in order; use the slower implementation
        return BLSpans.naiveAdvanceStartPosition(this, target);
    }

    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1;
        return startPos == NO_MORE_POSITIONS ? NO_MORE_POSITIONS : getRelationInfo().spanEnd(spanMode);
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        fetchedRelationInfo = false; // only decode payload if we need to

        if (spansMayIncludeRoots && spanMode == RelationInfo.SpanMode.SOURCE && getRelationInfo().isRoot()) {
            // Need source, but this has no source
            return FilterSpans.AcceptStatus.NO;
        }

        // See if it's the correct direction
        boolean acc;
        switch (direction) {
        case ROOT:
            acc = getRelationInfo().isRoot();
            break;
        case FORWARD:
            acc = getRelationInfo().getSourceStart() <= getRelationInfo().getTargetStart();
            break;
        case BACKWARD:
            acc = getRelationInfo().getSourceStart() >= getRelationInfo().getTargetStart();
            break;
        case BOTH_DIRECTIONS:
            acc = true;
            break;
        default:
            throw new IllegalArgumentException("Unknown filter: " + direction);
        }
        if (acc) {
            // We have a match. Set the start position.
            // (if span mode is SOURCE, we don't need to decode the payload)

            if (spanMode == RelationInfo.SpanMode.SOURCE) {
                assert candidate.startPosition() == getRelationInfo().spanStart(spanMode);
            }

            startPos = spanMode == RelationInfo.SpanMode.SOURCE ? candidate.startPosition() :
                    getRelationInfo().spanStart(spanMode);
        }
        return acc ? FilterSpans.AcceptStatus.YES : FilterSpans.AcceptStatus.NO;
    }

    /** SpanCollector that collects both the the payload and term for the current match. */
    private static class PayloadAndTermCollector extends PayloadSpanCollector {
        public Term term;

        @Override
        public void collectLeaf(PostingsEnum postings, int position, Term term) throws IOException {
            this.term = term;
            super.collectLeaf(postings, position, term);
        }
    }

    private final PayloadAndTermCollector collector = new PayloadAndTermCollector();

    @Override
    public String toString() {
        return "REL(" + relationType + ", " + spanMode + (direction != Direction.BOTH_DIRECTIONS ?
                ", " + direction :
                "") + ")";
    }
}
