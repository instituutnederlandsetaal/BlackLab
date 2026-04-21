package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.queries.spans.BLConjunctionSpans;

/**
 * Combines two Spans using AND. Note that this means that only matches with the
 * same document id, the same start and the same end positions will be kept.
 *
 * This version is for the "simple" case where we know both clauses have unique
 * spans. If clauses may have duplicate spans (i.e. same start/end but different
 * match info, use SpansAnd instead).
 */
class SpansAndSimple extends BLConjunctionSpans {

    /**
     * Construct SpansAnd.
     * <p>
     * Clauses must be start-point sorted.
     *
     * @param first first clause
     * @param second second clause
     */
    public SpansAndSimple(BLSpans first, BLSpans second) {
        super(List.of(first, second), SpanQueryAndNot.createGuarantees(List.of(first.guarantees(), second.guarantees()), false));

        if (!first.guarantees().hitsStartPointSorted())
            throw new IllegalArgumentException("First clause is not start point sorted!");
        if (!second.guarantees().hitsStartPointSorted())
            throw new IllegalArgumentException("Second clause is not start point sorted!");
    }

    @Override
    public int startPosition() {
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        return atFirstInCurrentDoc ? -1 : subSpans[0].startPosition();
    }

    @Override
    public int endPosition() {
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        return atFirstInCurrentDoc ? -1 : subSpans[0].endPosition();
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            assert subSpans[0].startPosition() >= 0 && subSpans[1].startPosition() >= 0;
            assert subSpans[0].startPosition() != NO_MORE_POSITIONS && subSpans[1].startPosition() != NO_MORE_POSITIONS;
            return subSpans[0].startPosition();
        }
        for (BLSpans subSpan: subSpans) {
            if (subSpan.nextStartPosition() == NO_MORE_POSITIONS) {
                oneExhaustedInCurrentDoc = true;
                return NO_MORE_POSITIONS;
            }
        }
        return synchronizePosition();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        int startPos = startPosition();
        assert target > startPos;
        if (atFirstInCurrentDoc) {
            startPos = nextStartPosition();
            if (startPos >= target)
                return startPos;
        }
        if (startPos >= target)
            return nextStartPosition(); // already at or beyond target. per contract, return next match
        for (BLSpans subSpan: subSpans) {
            if (subSpan.advanceStartPosition(target) == NO_MORE_POSITIONS) {
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
            int leftStart = subSpans[0].startPosition();
            int rightStart = subSpans[1].startPosition();

            // Synch at match start level
            if ((leftStart == -1 && rightStart == -1) ||
                    leftStart != rightStart) {
                // Starts don't match
                int laggingSpans = leftStart < rightStart ? 0 : 1;
                catchUpMatchStart(laggingSpans);
            } else if (subSpans[0].endPosition() != subSpans[1].endPosition()) {
                // Starts match but ends don't
                int laggingSpans = subSpans[0].endPosition() < subSpans[1].endPosition() ? 0 : 1;
                catchUpMatchEnd(laggingSpans);
            } else {
                // Both match
                if (leftStart == NO_MORE_POSITIONS) {
                    oneExhaustedInCurrentDoc = true;
                    return NO_MORE_POSITIONS;
                }
                assert leftStart >= 0;
                return leftStart;
            }
        }
    }

    /** See if we can get starts to line up. */
    private void catchUpMatchStart(int laggingSpans) throws IOException {
        int catchUpTo = subSpans[1 - laggingSpans].startPosition();
        int catchUpFrom = subSpans[laggingSpans].startPosition();
        if (catchUpFrom < catchUpTo || catchUpFrom == -1) { // also covers catchUpFrom != NO_MORE_POSITIONS
            if (subSpans[laggingSpans].advanceStartPosition(catchUpTo) == NO_MORE_POSITIONS)
                oneExhaustedInCurrentDoc = true;
        }
    }

    /** Try to get ends to line up without moving starts. */
    private void catchUpMatchEnd(int laggingSpans) throws IOException {
        int catchUpFromStart = subSpans[laggingSpans].startPosition();
        int catchUpToEnd = subSpans[1 - laggingSpans].endPosition();
        while ((subSpans[laggingSpans].startPosition() == catchUpFromStart &&
                subSpans[laggingSpans].endPosition() < catchUpToEnd) || subSpans[laggingSpans].startPosition() == -1) {
            if (subSpans[laggingSpans].nextStartPosition() == NO_MORE_POSITIONS)
                oneExhaustedInCurrentDoc = true;
        }
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        assert positionedInDoc();
        // Note that we DON't use our nextStartPosition() here because atFirstInCurrentDoc
        // is not properly set yet at this point in time (we do that below).
        atFirstInCurrentDoc = false;
        oneExhaustedInCurrentDoc = false;
        subSpans[0].nextStartPosition();
        subSpans[1].nextStartPosition();
        int start = synchronizePosition();
        if (start == NO_MORE_POSITIONS)
            return false;
        atFirstInCurrentDoc = true;
        return true;
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        super.getMatchInfo(matchInfo);
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
