package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Iterator;
import java.util.NavigableSet;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorLeafReader;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.fimatch.NfaState;

/**
 * Finds hits using the forward index, by matching an NFA from anchor points.
 */
class SpansFiSeq extends BLFilterDocsSpans<BLSpans> {

    /** Where to get forward index tokens for the current doc */
    private ForwardIndexDocument currentFiDoc;

    /** What start pos is the anchor at? */
    private int anchorStart = -1;

    /**
     * Are we already at the first match in a new document, before
     * nextStartPosition() has been called? Necessary because we have to make sure
     * nextDoc()/advance() actually puts us in a document with at least one match.
     */
    private boolean atFirstInCurrentDoc = false;

    /**
     * If true, match from the start of the anchor hit. Otherwise, match from the
     * end.
     */
    private final boolean startOfAnchor;

    /** The NFA to use to find matches in the forward index. */
    private final NfaState nfa;

    /** The direction to match in (-1 / DIR_TO_LEFT = backward, 1 / DIR_TO_RIGHT = forward). */
    private final int direction;

    /** Maps from term strings to term indices for each annotation. */
    private final ForwardIndexAccessorLeafReader fiAccessor;

    /** Iterator over NFA-matched endpoints */
    private Iterator<Integer> matchEndPointIt;

    /** Current NFA-matched endpoint */
    private int currentMatchEndPoint = -1;

    public SpansFiSeq(BLSpans anchorSpans, boolean startOfAnchor, NfaState nfa, int direction,
            ForwardIndexAccessorLeafReader fiAccessor, SpanGuarantees guarantees) {
        super(anchorSpans, guarantees);
        this.startOfAnchor = startOfAnchor;
        this.nfa = nfa;
        this.direction = direction;
        this.fiAccessor = fiAccessor;
    }

    @Override
    public int startPosition() {
        if (atFirstInCurrentDoc)
            return -1; // nextStartPosition() hasn't been called yet
        if (anchorStart == NO_MORE_POSITIONS || anchorStart < 0)
            return anchorStart;
        return direction < 0 ? Math.min(currentMatchEndPoint + 1, anchorStart) : anchorStart;
    }

    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1; // nextStartPosition() hasn't been called yet
        int endPos = in.endPosition();
        if (endPos == NO_MORE_POSITIONS || endPos < 0)
            return endPos;
        return direction > 0 ? Math.max(currentMatchEndPoint, in.endPosition()) : in.endPosition();
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        return super.nextDoc();
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (in.docID() == NO_MORE_DOCS)
            return NO_MORE_POSITIONS;

        if (atFirstInCurrentDoc) {
            // We're already at the first match in the doc. Return it.
            atFirstInCurrentDoc = false;
            return anchorStart;
        }

        // Are we done yet?
        if (anchorStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        if (matchEndPointIt.hasNext()) {
            currentMatchEndPoint = matchEndPointIt.next();
            return startPosition();
        }

        // Find first matching anchor span from here
        anchorStart = in.nextStartPosition();
        return synchronizePos();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        assert target > startPosition();
        if (in.docID() == NO_MORE_DOCS)
            return NO_MORE_POSITIONS;

        if (atFirstInCurrentDoc) {
            int startPos = nextStartPosition();
            if (startPos >= target)
                return startPos;
        }

        // Are we done yet?
        if (anchorStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        anchorStart = in.advanceStartPosition(target);

        // Find first matching anchor span from here
        return synchronizePos();
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        assert positionedInDoc();
        // Are there search results in this document?
        atFirstInCurrentDoc = false;
        matchEndPointIt = null;
        if (in.startPosition() != NO_MORE_POSITIONS) {
            anchorStart = in.nextStartPosition();
        }
        anchorStart = synchronizePos();
        if (anchorStart == NO_MORE_POSITIONS)
            return false;
        atFirstInCurrentDoc = true;
        return true;
    }

    /**
     * Find a anchor span that has an NFA match, starting from the current anchor
     * span.
     *
     * @return start position if found, NO_MORE_POSITIONS if no such anchor spans
     *         exists (i.e. we're done)
     */
    private int synchronizePos() throws IOException {
        if (currentFiDoc == null || currentFiDoc.getSegmentDocId() != docID())
            currentFiDoc = fiAccessor.getForwardIndexDoc(docID());

        // Find the next "valid" anchor spans, if there is one.
        while (anchorStart != NO_MORE_POSITIONS) {

            // We're at the first unchecked anchor spans. Does our NFA match?
            int anchorPos = startOfAnchor ? anchorStart : in.endPosition();
            if (direction < 0)
                anchorPos--;
            // OPT: sometimes anchorPos may be the same as the previous one. We could check for
            //      this to avoid re-running the NFA. This is likely fairly rare though.
            NavigableSet<Integer> setMatchEndpoints = nfa.findMatches(currentFiDoc, anchorPos, direction);
            if (!setMatchEndpoints.isEmpty()) {
                if (direction == 1)
                    matchEndPointIt = setMatchEndpoints.iterator();
                else
                    matchEndPointIt = setMatchEndpoints.descendingSet().iterator();
                currentMatchEndPoint = matchEndPointIt.next();
                return startPosition();
            }

            // Didn't match filter; go to the next position.
            anchorStart = in.nextStartPosition();
            if (anchorStart == NO_MORE_POSITIONS)
                return NO_MORE_POSITIONS;

        }
        return anchorStart;
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        atFirstInCurrentDoc = false;
        return super.advance(target);
    }

    @Override
    public String toString() {
        return "SpansFiSeq(" + in + ", " + (startOfAnchor ? "START" : "END") + ", " + nfa + ", "
                + (direction == 1 ? "FORWARD" : "BACKWARD") + ")";
    }

}
