package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.search.spans.FilterSpans;

/**
 * Adjust the start and end of hits while matching.
 */
class SpansAdjustHits extends BLFilterSpans<BLSpans> {

    /** How to adjust the starts of the hits. */
    private final int startAdjust;

    /** How to adjust the ends of the hits. */
    private final int endAdjust;

    /** Used to get the field length in tokens for a document */
    private final DocFieldLengthGetter lengthGetter;

    /** Which doc do we know the length for? */
    private int docLengthDocId = -1;

    /** What's the document's length? */
    private int docLength = -1;

    /**
     * Constructs a SpansCaptureGroup.
     *
     * @param clause the clause to capture
     * @param startAdjust how to adjust start positions
     * @param endAdjust how to adjust end positions
     */
    public SpansAdjustHits(LeafReader reader, String fieldName, BLSpans clause, int startAdjust, int endAdjust) {
        super(clause);
        this.startAdjust = startAdjust;
        this.endAdjust = endAdjust;
        this.lengthGetter = new DocFieldLengthGetter(reader, fieldName);
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        int start = candidate.startPosition() + startAdjust;
        int end = candidate.endPosition() + endAdjust;
        int length = getDocLength(candidate.docID());
        if (start < 0 || end < 0 || start >= length || end > length || start > end) {
            // After adjustment, the hit is outside the document or invalid. Skip it.
            return FilterSpans.AcceptStatus.NO;
        }
        return FilterSpans.AcceptStatus.YES;
    }

    private int getDocLength(int docId) {
        if (docId != docLengthDocId) {
            if (docId < docLengthDocId)
                throw new IllegalArgumentException("Spans out of order: " + docId + " < " + docLengthDocId);
            docLengthDocId = docId;
            docLength = lengthGetter.getFieldLength(docId);
        }
        return docLength;
    }

    @Override
    public String toString() {
        String adj = (startAdjust != 0 || endAdjust != 0 ? ", " + startAdjust + ", " + endAdjust : "");
        return "ADJUST(" + in + adj + ")";
    }

    @Override
    public int nextStartPosition() throws IOException {
        super.nextStartPosition();
        return startPosition();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        super.advanceStartPosition(target);
        return startPosition();
    }

    @Override
    public int startPosition() {
        int start = super.startPosition();
        return start >= 0 && start != NO_MORE_POSITIONS ? start + startAdjust : start;
    }

    @Override
    public int endPosition() {
        int start = super.startPosition();
        return start >= 0 && start != NO_MORE_POSITIONS ? super.endPosition() + endAdjust : super.endPosition();
    }

    @Override
    public boolean hasMatchInfo() {
        return in.hasMatchInfo();
    }
}
