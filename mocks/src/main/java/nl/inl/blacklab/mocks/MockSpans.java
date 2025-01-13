package nl.inl.blacklab.mocks;

import java.io.IOException;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanGuarantees;

/**
 * Stub Spans class for testing. Takes arrays and iterates through 'hits' from
 * these arrays.
 */
public class MockSpans extends BLSpans {

    static RelationsStrategy relationsStrategy = RelationsStrategy.forNewIndex();

    private final class MockPostingsEnum extends PostingsEnum {
        private int currentDoc = -1;
        private int currentHit = -1;
        private boolean atFirstInCurrentDoc = false;

        public MockPostingsEnum() {
            // NOP
        }

        @Override
        public int nextDoc() {
            if (currentDoc != NO_MORE_DOCS) {
                atFirstInCurrentDoc = false;
                while (currentHit < doc.length && (currentHit == -1 || doc[currentHit] == currentDoc)) {
                    currentHit++;
                }
                if (currentHit >= doc.length) {
                    currentDoc = NO_MORE_DOCS;
                    return NO_MORE_DOCS;
                }
                atFirstInCurrentDoc = true;
                currentDoc = doc[currentHit];
            }
            return currentDoc;
        }

        @Override
        public int docID() {
            return currentDoc;
        }

        @Override
        public long cost() {
            return 0;
        }

        @Override
        public int advance(int target) {
            if (currentDoc != NO_MORE_DOCS) {
                atFirstInCurrentDoc = false;
                do {
                    currentDoc = nextDoc();
                } while (currentDoc != NO_MORE_DOCS && currentDoc < target);
            }
            return currentDoc;
        }

        @Override
        public int startOffset() {
            if (currentHit < 0 || atFirstInCurrentDoc)
                return -1;
            if (currentDoc == NO_MORE_DOCS || currentHit >= doc.length || doc[currentHit] != currentDoc)
                return NO_MORE_POSITIONS;
            return start[currentHit];
        }

        @Override
        public int nextPosition() {
            if (currentDoc == NO_MORE_DOCS)
                return NO_MORE_POSITIONS;
            if (atFirstInCurrentDoc) {
                atFirstInCurrentDoc = false;
                return startOffset();
            }
            if (currentHit < 0)
                throw new BlackLabRuntimeException("nextDoc() not called yet!");
            if (currentHit < doc.length && doc[currentHit] == currentDoc) {
                currentHit++;
                return startOffset(); // may return NO_MORE_POSITIONS if we're at the next doc
            }
            return NO_MORE_POSITIONS;
        }

        @Override
        public BytesRef getPayload() {
            if (payloads == null)
                return null;
            if (currentHit < 0 || atFirstInCurrentDoc)
                return null;
            if (currentDoc == NO_MORE_DOCS || currentHit >= doc.length || doc[currentHit] != currentDoc)
                return null;
            return payloads[currentHit];
        }

        @Override
        public int freq() {
            // Find start of next document
            int i;
            i = currentHit + 1;
            while (i < doc.length && doc[i] == currentDoc) {
                i++;
            }
            return i - currentHit;
        }

        @Override
        public int endOffset() {
            if (currentHit < 0 || atFirstInCurrentDoc)
                return -1;
            if (currentDoc == NO_MORE_DOCS || currentHit >= doc.length || doc[currentHit] != currentDoc)
                return NO_MORE_POSITIONS;
            return end[currentHit];
        }
    }

    private final int[] doc;

    private final int[] start;

    private final int[] end;

    private final MockPostingsEnum postings;

    private final MyTermSpans spans;

    private boolean noMoreDocs = false;

    private boolean noMoreHitsInDoc = true;

    private BytesRef[] payloads = null;

    private int endPos = -1;

    public void setGuarantees(SpanGuarantees guarantees) {
        this.guarantees = guarantees;
    }

    public MockSpans(int[] doc, int[] start, int[] end, SpanGuarantees guarantees) {
        super(guarantees);
        this.doc = doc;
        this.start = start;
        this.end = end;
        postings = new MockPostingsEnum();
        spans = new MyTermSpans(postings, new Term("test", "dummy"), 1);
    }

    public MockSpans(int[] doc, int[] start, int[] end) {
        this(doc, start, end, SpanGuarantees.SORTED_UNIQUE);
    }

    @Override
    public int docID() {
        return spans.docID();
    }

    @Override
    public int endPosition() {
        return endPos; //spans.endPosition();
    }

    private void setPayloadsInt(int[] aStart, int[] aEnd, boolean[] aIsPrimary) {
        this.payloads = new BytesRef[aEnd.length];
        for (int i = 0; i < aEnd.length; i++) {
            BytesRef payload = relationsStrategy.getPayloadCodec().inlineTagPayload(aStart[i], aEnd[i],
                    BlackLabIndex.IndexType.EXTERNAL_FILES, -1, true);
            if (aIsPrimary != null)
                payload = PayloadUtils.addIsPrimary(aIsPrimary[i], payload);
            this.payloads[i] = payload;
        }
    }

    private void setPayloadsRelationsInt(int[] aStart, int[] aEnd, boolean[] aIsPrimary) {
        this.payloads = new BytesRef[aEnd.length];
        for (int i = 0; i < aEnd.length; i++) {
            RelationInfo relInfo = RelationInfo.create(false, aStart[i], aStart[i],
                    aEnd[i], aEnd[i], 0, "test", false);
            BytesRef payload = relationsStrategy.getPayloadCodec().serialize(relInfo);
            if (aIsPrimary != null)
                payload = PayloadUtils.addIsPrimary(aIsPrimary[i], payload);
            this.payloads[i] = payload;
        }
    }

    private void setPayloadsMatchInfo(int[] aStart, int[] aEnd, RelationInfo[] relationInfo) {
        this.payloads = new BytesRef[aEnd.length];
        for (int i = 0; i < aEnd.length; i++) {
            this.payloads[i] = relationsStrategy.getPayloadCodec().serialize(relationInfo[i]);
        }
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        if (noMoreDocs)
            throw new BlackLabRuntimeException("Called nextDoc() on exhausted spans!");
        endPos = -1;
        int docId = spans.nextDoc();
        if (docId == NO_MORE_DOCS)
            noMoreDocs = true;
        else
            noMoreHitsInDoc = false;
        return docId;
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (noMoreHitsInDoc)
            throw new BlackLabRuntimeException("Called nextStartPosition() on hit-exhausted spans!");
        int startPos = spans.nextStartPosition();
        endPos = startPos == NO_MORE_POSITIONS ? NO_MORE_POSITIONS : postings.endOffset();
        if (startPos == NO_MORE_POSITIONS) {
            noMoreHitsInDoc = true;
        }
        return startPos;
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        if (noMoreDocs)
            throw new BlackLabRuntimeException("Called advance() on exhausted spans!");
        endPos = -1;
        if (target <= spans.docID())
            throw new IllegalArgumentException("target <= doc (" + target + " <= " + spans.docID() + ")");
        int docId = spans.advance(target);
        if (docId == NO_MORE_DOCS)
            noMoreDocs = true;
        else
            noMoreHitsInDoc = false;
        return docId;
    }

    @Override
    public int startPosition() {
        return spans.startPosition();
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        // just ignore this here
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        // NOP
    }

    @Override
    public boolean hasMatchInfo() {
        return false;
    }

    @Override
    public RelationInfo getRelationInfo() {
        return null;
    }

    public static MockSpans emptySpans() {
        return new MockSpans(new int[0], new int[0], new int[0]);
    }

    public static MockSpans single(int doc, int start, int end) {
        return new MockSpans(new int[] { doc }, new int[] { start }, new int[] { end });
    }

    public static MockSpans fromLists(int[] doc, int[] start, int[] end) {
        return new MockSpans(doc, start, end);
    }

    public static MockSpans withEndInPayload(int[] aDoc, int[] aStart, int[] aEnd, boolean[] aIsPrimary) {
        MockSpans spans = MockSpans.singleWordSpans(aDoc, aStart);
        spans.setPayloadsInt(aStart, aEnd, aIsPrimary);
        return spans;
    }

    public static MockSpans withRelationInfoInPayload(int[] aDoc, int[] aStart, int[] aEnd, boolean[] aIsPrimary) {
        MockSpans spans = MockSpans.singleWordSpans(aDoc, aStart);
        spans.setPayloadsRelationsInt(aStart, aEnd, aIsPrimary);
        return spans;
    }

    public static MockSpans withRelationInfoObjectsInPayload(int[] aDoc, int[] aStart, int[] aEnd, RelationInfo[] aRelationInfo) {
        MockSpans spans = MockSpans.singleWordSpans(aDoc, aStart);
        spans.setPayloadsMatchInfo(aStart, aEnd, aRelationInfo);
        return spans;
    }

    private static MockSpans singleWordSpans(int[] aDoc, int[] aStart) {
        int[] aEnd = new int[aStart.length];
        for (int i = 0; i < aStart.length; i++) {
            aEnd[i] = aStart[i] + 1;
        }
        return fromLists(aDoc, aStart, aEnd);
    }

    @Override
    public int width() {
        return 0;
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        collector.collectLeaf(postings, -1, null);
    }

    @Override
    public float positionsCost() {
        return start.length;
    }

}
