package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorLeafReader;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;

/**
 * Finds hits using the forward index, by matching an NFA from anchor points.
 */
class SpansCollocations extends BLFilterDocsSpans<BLSpans> {

    /** Where to get forward index tokens for the current doc */
    private ForwardIndexDocument currentFiDoc;

    /** What start pos is the anchor at? */
    private int keywordStart = -1;

    /**
     * Are we already at the first match in a new document, before
     * nextStartPosition() has been called? Necessary because we have to make sure
     * nextDoc()/advance() actually puts us in a document with at least one match.
     */
    private boolean atFirstInCurrentDoc = false;

    /** The NFA used to find collocates after the keyword. */
    private final NfaState nfaForward;

    /** The NFA used to find collocates before the keyword. */
    private final NfaState nfaBackward;

    /** minimum gap between keyword and collocate */
    final SpanQueryCollocations.CollocationContext collocationContext;

    /** Maps from term strings to term indices for each annotation. */
    private final ForwardIndexAccessorLeafReader fiAccessor;

    /** Used to store list of collocates found. */
    record Span(int start, int end) implements Comparable<Span> {
        @Override
        public int compareTo(Span other) {
            int cmp = Integer.compare(this.start, other.start);
            if (cmp != 0) return cmp;
            return Integer.compare(this.end, other.end);
        }
    }

    /** Iterator over NFA-matched endpoints */
    private Iterator<Span> collocateIterator;

    /** Current NFA-matched endpoint */
    private Span currentCollocate = null;

    public SpansCollocations(BLSpans anchorSpans, SpanGuarantees guarantees,
            SpanQueryCollocations.CollocationContext collocationContext, LeafReaderContext lrc,
            ForwardIndexAccessor fiAccessor, NfaTwoWay nfa) {
        super(anchorSpans, guarantees);
        this.collocationContext = collocationContext;
        this.fiAccessor = fiAccessor.getForwardIndexAccessorLeafReader(lrc);
        this.nfaForward = nfa.getNfa().getStartingState().forSegment(lrc);
        this.nfaBackward = nfa.getNfaReverse().getStartingState().forSegment(lrc);
    }

    @Override
    public int startPosition() {
        if (atFirstInCurrentDoc)
            return -1; // nextStartPosition() hasn't been called yet
        if (keywordStart == NO_MORE_POSITIONS || keywordStart < 0)
            return keywordStart;
        return currentCollocate.start;
    }

    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1; // nextStartPosition() hasn't been called yet
        int endPos = in.endPosition();
        if (endPos == NO_MORE_POSITIONS || endPos < 0)
            return endPos;
        return currentCollocate.end;
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
            return keywordStart;
        }

        // Are we done yet?
        if (keywordStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        if (collocateIterator.hasNext()) {
            currentCollocate = collocateIterator.next();
            return startPosition();
        }

        // Find first matching anchor span from here
        keywordStart = in.nextStartPosition();
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
        if (keywordStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        keywordStart = in.advanceStartPosition(target);

        // Find first matching anchor span from here
        return synchronizePos();
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        assert positionedInDoc();
        // Are there search results in this document?
        atFirstInCurrentDoc = false;
        collocateIterator = null;
        if (in.startPosition() != NO_MORE_POSITIONS) {
            keywordStart = in.nextStartPosition();
        }
        keywordStart = synchronizePos();
        if (keywordStart == NO_MORE_POSITIONS)
            return false;
        atFirstInCurrentDoc = true;
        return true;
    }

    /**
     * Find a keyword that has collocate(s), starting from the current keyword.
     *
     * @return start position of first collocate found, or NO_MORE_POSITIONS if no
     *         more collocates exist (i.e. we're done)
     */
    private int synchronizePos() throws IOException {
        if (currentFiDoc == null || currentFiDoc.getSegmentDocId() != docID())
            currentFiDoc = fiAccessor.getForwardIndexDoc(docID());

        // Find the next "valid" anchor spans, if there is one.
        while (keywordStart != NO_MORE_POSITIONS) {
            List<Span> collocatesFound = new ArrayList<>();
            findCollocates(SpanQueryFiSeq.DIR_BACKWARD, collocationContext.before(), collocatesFound);
            Collections.sort(collocatesFound); // collocates before search backwards so may be out of order
            findCollocates(SpanQueryFiSeq.DIR_FORWARD, collocationContext.after(), collocatesFound);
            if (!collocatesFound.isEmpty()) {
                collocateIterator = collocatesFound.iterator();
                currentCollocate = collocateIterator.next();
                return startPosition();
            }

            // Didn't match filter; go to the next position.
            keywordStart = in.nextStartPosition();
        }
        return keywordStart;
    }

    private void findCollocates(int direction, SequenceGap gap, List<Span> collocatesFound) {
        boolean isForward = direction == SpanQueryFiSeq.DIR_FORWARD;
        int startFromPos = isForward ?
                in.endPosition() + gap.min() + 1 :
                keywordStart - gap.max() - 1;
        int endAtPos = isForward ?
                in.endPosition() + gap.max() + 1 :
                keywordStart - gap.min() - 1;
        NfaState nfa = isForward ? nfaForward : nfaBackward;

        for (int collocateStart = startFromPos; collocateStart != endAtPos; collocateStart += direction) {
            NavigableSet<Integer> collocateEnds = nfa.findMatches(currentFiDoc, collocateStart, direction);
            for (Integer collocateEnd: collocateEnds) {
                collocatesFound.add(isForward ? new Span(collocateStart, collocateEnd) : new Span(collocateEnd, collocateStart));
            }
        }
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        atFirstInCurrentDoc = false;
        return super.advance(target);
    }

    @Override
    public String toString() {
        return "SpansCollocations(" + in + ", " + nfaForward + ", " + collocationContext + ")";
    }

}
