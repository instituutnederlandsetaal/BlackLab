package nl.inl.blacklab.mocks;

import java.io.IOException;

import org.apache.commons.compress.archivers.dump.UnsupportedCompressionAlgorithmException;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.SpanGuarantees;
import nl.inl.blacklab.search.lucene.SpansInBuckets;

/**
 * Stub SpansInBuckets class for testing. Takes arrays and iterates through
 * 'hits' from these arrays.
 */
public class MockSpansInBuckets extends SpansInBuckets {

    public static SpansInBuckets fromLists(int[] bDoc, int[] bStart,
            int[] hStart, int[] hEnd) {
        return new MockSpansInBuckets(bDoc, bStart, hStart, hEnd);
    }

    private final int[] start;

    private final int[] end;

    private final int[] bucketStart;

    private final int[] bucketDoc;

    private int currentBucket = -1;

    private boolean atFirstInCurrentDoc = false;

    public MockSpansInBuckets(int[] bucketDoc, int[] bucketStart, int[] start, int[] end) {
        super(null);
        this.bucketDoc = bucketDoc;
        this.bucketStart = bucketStart;
        this.start = start;
        this.end = end;
    }

    @Override
    public int docID() {
        if (currentBucket >= bucketDoc.length)
            return DocIdSetIterator.NO_MORE_DOCS;
        return bucketDoc[currentBucket];
    }

    @Override
    public int endPosition(int i) {
        if (currentBucket >= bucketDoc.length)
            return Spans.NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc)
            return -1;
        return end[bucketStart[currentBucket] + i];
    }

    @Override
    public int startPosition(int i) {
        if (currentBucket >= bucketDoc.length)
            return Spans.NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc)
            return -1;
        return start[bucketStart[currentBucket] + i];
    }

    @Override
    public int nextBucket() {
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            return docID();
        }
        if (currentBucket >= bucketDoc.length - 1) {
            currentBucket = bucketDoc.length;
            return NO_MORE_BUCKETS; // completely done
        }
        if (bucketDoc[currentBucket + 1] != bucketDoc[currentBucket])
            return NO_MORE_BUCKETS; // no more in this doc
        currentBucket++;
        return docID();
    }

    @Override
    public int advanceBucket(int targetPos) throws IOException {
        throw new UnsupportedCompressionAlgorithmException("Not implemented");
    }

    @Override
    public int nextDoc() {
        if (currentBucket >= bucketDoc.length)
            return DocIdSetIterator.NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        int curDoc = currentBucket == -1 ? -1 : bucketDoc[currentBucket];
        while (currentBucket < bucketDoc.length && (currentBucket == -1 || bucketDoc[currentBucket] == curDoc)) {
            currentBucket++;
        }
        if (currentBucket == bucketDoc.length)
            return DocIdSetIterator.NO_MORE_DOCS;
        atFirstInCurrentDoc = true;
        return docID();
    }

    @Override
    public int advance(int target) {
        assert target >= 0 && target > docID();
        atFirstInCurrentDoc = false;
        int doc;
        do {
            doc = nextDoc();
        } while (doc != DocIdSetIterator.NO_MORE_DOCS && doc < target);
        if (doc != DocIdSetIterator.NO_MORE_DOCS)
            atFirstInCurrentDoc = true;
        return doc;
    }

    @Override
    public int bucketSize() {
        if (atFirstInCurrentDoc)
            return -1;
        if (currentBucket >= bucketDoc.length)
            return -1;
        if (currentBucket == bucketDoc.length - 1)
            return start.length - bucketStart[currentBucket];
        return bucketStart[currentBucket + 1] - bucketStart[currentBucket];
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        // NOP
    }

    @Override
    public void getMatchInfo(int indexInBucket, MatchInfo[] matchInfo) {
        // NOP
    }

    @Override
    public boolean hasMatchInfo() {
        return false;
    }

    @Override
    public SpanGuarantees guarantees() {
        return SpanGuarantees.NONE;
    }

    @Override
    public long cost() {
        return 0;
    }

    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        return null;
    }

    @Override
    public float positionsCost() {
        return start.length;
    }

    @Override
    public int width() {
        return 0;
    }

    @Override
    public String toString() {
        return "SIB-MOCK(" + source + ")";
    }
}
