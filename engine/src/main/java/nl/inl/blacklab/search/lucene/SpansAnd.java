package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.search.ConjunctionUtils;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.queries.spans.SpanCollector;

/**
 * Combines two Spans using AND. Note that this means that only matches with the
 * same document id, the same start and the same end positions will be kept.
 *
 * This version can deal with clauses where spans may have the same start/end
 * but different match info). If your spans don't, prefer the faster SpansAndSimple.
 */
class SpansAnd extends BLSpans {

    boolean oneExhaustedInCurrentDoc = false;

    boolean atFirstInCurrentDoc = true;

    /** Bucketed clauses */
    SpansInBucketsSameStartEnd[] subSpans = new SpansInBucketsSameStartEnd[2];

    /** Index in current bucket */
    int[] index = new int[] { -1, -1 };

    /** DocIdSetIterator conjunction of our clauses, for two-phase iterator */
    private final DocIdSetIterator conjunction;

    /**
     * Construct SpansAnd.
     * <p>
     * Clauses must be start-point sorted.
     *
     * @param first first clause
     * @param second second clause
     */
    public SpansAnd(BLSpans first, BLSpans second) {
        super(SpanQueryAnd.createGuarantees(List.of(first.guarantees(), second.guarantees()), false));
        if (!first.guarantees().hitsStartPointSorted())
            throw new IllegalArgumentException("First clause is not start-point sorted");
        if (!second.guarantees().hitsStartPointSorted())
            throw new IllegalArgumentException("Second clause is not start-point sorted");
        subSpans[0] = new SpansInBucketsSameStartEnd(first);
        subSpans[1] = new SpansInBucketsSameStartEnd(second);
        this.conjunction = ConjunctionUtils.intersectIterators(List.of(subSpans[0], subSpans[1]));
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        int doc = conjunction.nextDoc();
        if (doc == NO_MORE_DOCS)
            return NO_MORE_DOCS;
        assert Arrays.stream(subSpans).allMatch(a -> a.docID() == doc);
        return toMatchDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        atFirstInCurrentDoc = false;
        int doc = conjunction.advance(target);
        if (doc == NO_MORE_DOCS)
            return NO_MORE_DOCS;
        assert Arrays.stream(subSpans).allMatch(a -> a.docID() == doc);
        return toMatchDoc();
    }

    int toMatchDoc() throws IOException {
        while (true) {
            if (twoPhaseCurrentDocMatches()) {
                return docID();
            }
            int doc = conjunction.nextDoc();
            if (doc == NO_MORE_DOCS)
                return NO_MORE_DOCS;
            assert Arrays.stream(subSpans).allMatch(a -> a.docID() == doc);
        }
    }

    @Override
    public int docID() {
        return subSpans[0].docID();
    }

    @Override
    public int startPosition() {
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        return atFirstInCurrentDoc ? -1 : subSpans[0].startPosition(index[0]);
    }

    @Override
    public int endPosition() {
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        return atFirstInCurrentDoc ? -1 : subSpans[0].endPosition(index[0]);
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            assert index[0] >= 0 && index[1] >= 0;
            assert index[0] < subSpans[0].bucketSize() && index[1] < subSpans[1].bucketSize();
            assert subSpans[0].startPosition(index[0]) >= 0 && subSpans[1].startPosition(index[1]) >= 0;
            assert subSpans[0].startPosition(index[0]) != NO_MORE_POSITIONS && subSpans[1].startPosition(index[1]) != NO_MORE_POSITIONS;
            return subSpans[0].startPosition(index[0]);
        }

        // See if there's more combinations for the current start/end
        index[1]++;
        if (index[1] >= subSpans[1].bucketSize()) {
            index[1] = 0;
            index[0]++;
        }
        if (index[0] < subSpans[0].bucketSize())
            return startPosition();

        // Go to next bucket
        for (SpansInBucketsSameStartEnd subSpan: subSpans) {
            if (subSpan.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
                oneExhaustedInCurrentDoc = true;
                return NO_MORE_POSITIONS;
            }
        }
        return synchronizePosition();
    }

    @Override
    public int advanceStartPosition(int targetPos) throws IOException {
        assert targetPos > startPosition();
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        int startPos = startPosition();
        if (startPos >= targetPos)
            return nextStartPosition(); // already at or beyond target. per contract, return next match
        for (SpansInBucketsSameStartEnd subSpan: subSpans) {
            if (subSpan.advanceBucket(targetPos) == SpansInBuckets.NO_MORE_BUCKETS) {
                oneExhaustedInCurrentDoc = true;
                return NO_MORE_POSITIONS;
            }
        }
        return synchronizePosition();
    }

    private int synchronizePosition() throws IOException {
        while (true) {
            if (oneExhaustedInCurrentDoc)
                return NO_MORE_POSITIONS;
            int leftStart = subSpans[0].bucketStart();
            int rightStart = subSpans[1].bucketStart();

            // Synch at match start level
            if ((leftStart == -1 && rightStart == -1) ||
                    leftStart != rightStart) {
                // Starts don't match
                int laggingSpans = leftStart < rightStart ? 0 : 1;
                catchUpMatchStart(laggingSpans);
            } else if (subSpans[0].bucketEnd() != subSpans[1].bucketEnd()) {
                // Starts match but ends don't
                int laggingSpans = subSpans[0].bucketEnd() < subSpans[1].bucketEnd() ? 0 : 1;
                catchUpMatchEnd(laggingSpans);
            } else {
                // Both match
                assert leftStart >= 0;
                index[0] = index[1] = 0;
                return leftStart;
            }
        }
    }

    /** See if we can get starts to line up. */
    private void catchUpMatchStart(int laggingSpans) throws IOException {
        int catchUpTo = subSpans[1 - laggingSpans].bucketStart();
        int catchUpFrom = subSpans[laggingSpans].bucketStart();
        if (catchUpFrom < catchUpTo || catchUpFrom == -1) { // also covers catchUpFrom != NO_MORE_POSITIONS
            if (subSpans[laggingSpans].advanceBucket(catchUpTo) == SpansInBuckets.NO_MORE_BUCKETS)
                oneExhaustedInCurrentDoc = true;
        }
    }

    /** Try to get ends to line up without moving starts. */
    private void catchUpMatchEnd(int laggingSpans) throws IOException {
        int catchUpFromStart = subSpans[laggingSpans].bucketStart();
        int catchUpToEnd = subSpans[1 - laggingSpans].bucketEnd();
        while ((subSpans[laggingSpans].bucketStart() == catchUpFromStart &&
                subSpans[laggingSpans].bucketEnd() < catchUpToEnd) || subSpans[laggingSpans].bucketStart() == -1) {
            if (subSpans[laggingSpans].nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
                oneExhaustedInCurrentDoc = true;
                break;
            }
        }
    }

    boolean twoPhaseCurrentDocMatches() throws IOException {
        assert positionedInDoc();
        // Note that we DON't use our nextStartPosition() here because atFirstInCurrentDoc
        // is not properly set yet at this point in time (we do that below).
        atFirstInCurrentDoc = false;
        oneExhaustedInCurrentDoc = false;
        subSpans[0].nextBucket();
        subSpans[1].nextBucket();
        int start = synchronizePosition();
        if (start == NO_MORE_DOCS)
            return false;
        index[0] = index[1] = 0;
        atFirstInCurrentDoc = true;
        return true;
    }

    /**
     * Return a {@link TwoPhaseIterator} view of this Spans.
     */
    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        float totalMatchCost = 0;
        // Compute the matchCost as the total matchCost/positionsCostant of the sub spans.
        for (SpansInBuckets spans : subSpans) {
            TwoPhaseIterator tpi = spans.asTwoPhaseIterator();
            totalMatchCost += tpi != null ? tpi.matchCost() : spans.positionsCost();
        }
        final float matchCost = totalMatchCost;

        return new TwoPhaseIterator(conjunction) {
            @Override
            public boolean matches() throws IOException {
                return twoPhaseCurrentDocMatches();
            }

            @Override
            public float matchCost() {
                return matchCost;
            }
        };
    }

    @Override
    public float positionsCost() {
        throw new UnsupportedOperationException(); // asTwoPhaseIterator never returns null here.
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        // not implemented, but not needed
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        subSpans[0].getMatchInfo(index[0], matchInfo);
        subSpans[1].getMatchInfo(index[1], matchInfo);
    }

    @Override
    public boolean hasMatchInfo() {
        return Arrays.stream(subSpans).anyMatch(SpansInBuckets::hasMatchInfo);
    }

    @Override
    public RelationInfo getRelationInfo() {
        for (int i = 0; i < subSpans.length; i++) {
            RelationInfo info = subSpans[i].getRelationInfo(index[i]);
            if (info != null)
                return info;
        }
        return null;
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        for (SpansInBuckets subSpan: subSpans) {
            subSpan.setHitQueryContext(context);
        }
    }

    @Override
    public String toString() {
        return "AND(" + subSpans[0] + ", " + subSpans[1] + ")";
    }

    @Override
    public int width() {
        return subSpans[0].width() + subSpans[1].width();
    }

}
