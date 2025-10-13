package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.search.BlackLabIndexAbstract;

/**
 * Return all n-grams of certain lengths.
 */
class SpansNGrams extends BLSpans {
    /** Current document */
    private int currentDoc = -1;

    /** Current document length */
    private long currentDocLength = -1;

    /** Current hit start position */
    private int currentStart = -1;

    /** Current hit end position */
    private int currentEnd = -1;

    /** Used to get the field length in tokens for a document */
    final DocFieldLengthGetter lengthGetter;

    /** Highest document id plus one */
    private int maxDoc;

    /** Documents that haven't been deleted */
    private final Bits liveDocs;

    private boolean atFirstInCurrentDoc = false;

    private final int min;

    private final int max;

    /**
     * For testing, we don't have an IndexReader available, so we use test values.
     *
     * The test values are: there are 3 documents (0, 1 and 2) and each is 5 tokens
     * long.
     *
     * @param maxDoc number of docs in the (mock) test set
     */
    void setTest(int maxDoc) {
        // For testing, we don't have an IndexReader available, so we use test values
        this.maxDoc = maxDoc;
        lengthGetter.setTest(true);
    }

    /**
     * Constructs a SpansNGrams
     *
     * @param reader the index reader, for getting field lengths
     * @param fieldName the field name, for getting field lengths
     * @param min minimum n-gram length
     * @param max maximum n-gram length
     */
    public SpansNGrams(LeafReader reader, String fieldName, int min, int max) {
        super(SpanQueryAnyToken.createGuarantees(min, max));
        maxDoc = reader == null ? -1 : reader.maxDoc();
        liveDocs = reader == null ? null : MultiBits.getLiveDocs(reader);
        this.lengthGetter = new DocFieldLengthGetter(reader, fieldName);
        this.min = min;
        this.max = max;
    }

    /**
     * @return the Lucene document id of the current hit
     */
    @Override
    public int docID() {
        return currentDoc;
    }

    /**
     * @return end position of current hit
     */
    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1; // .nextStartPosition() not called yet by client
        return currentEnd;
    }

    @Override
    public int nextDoc() {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        do {
            if (currentDoc >= maxDoc) {
                currentDoc = NO_MORE_DOCS;
                currentStart = currentEnd = NO_MORE_POSITIONS;
                return NO_MORE_DOCS;
            }
            // Go to next nondeleted doc
            boolean currentDocIsDeletedDoc;
            do {
                currentDoc++;
                currentDocIsDeletedDoc = currentDoc < maxDoc && liveDocs != null && !liveDocs.get(currentDoc);
            } while (currentDoc < maxDoc && currentDocIsDeletedDoc);
            if (currentDoc > maxDoc)
                throw new IllegalStateException("currentDoc > maxDoc!!");
            if (currentDoc == maxDoc) {
                currentDoc = NO_MORE_DOCS;
                currentStart = currentEnd = NO_MORE_POSITIONS;
                return NO_MORE_DOCS; // no more docs; we're done
            }
            // Get document length and reset currentStart/currentEnd so we can check if there's actually hits
            currentDocLength = (long)lengthGetter.getFieldLength(currentDoc) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
            currentStart = currentEnd = -1;
        } while (currentDocLength < min || nextStartPosition() == NO_MORE_POSITIONS);
        atFirstInCurrentDoc = true;

        return currentDoc;
    }

    /**
     * Go to next span.
     *
     * @return true if we're at the next span, false if we're done
     */
    @Override
    public int nextStartPosition() {
        assert startPosition() != NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            return currentStart;
        }

        if (currentDoc == NO_MORE_DOCS || currentStart == NO_MORE_POSITIONS) {
            return NO_MORE_POSITIONS;
        }

        if (currentDoc < 0)
            return -1; // haven't started

        // Next N-gram
        if (currentStart < 0 || currentEnd - currentStart >= max || currentEnd >= currentDocLength) {
            currentStart++;
            currentEnd = currentStart + min;
            if (currentEnd > currentDocLength) {
                currentStart = currentEnd = NO_MORE_POSITIONS;
                return NO_MORE_POSITIONS;
            }
        } else {
            currentEnd++;
        }
        return currentStart;
    }

    @Override
    public int advanceStartPosition(int target) {
        assert target > startPosition();
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            if (currentStart >= target)
                return currentStart;
        }
        if (target >= currentDocLength) {
            currentStart = currentEnd = NO_MORE_POSITIONS;
            return NO_MORE_POSITIONS;
        }
        // Advance us to just before the requested start point, then call nextStartPosition().
        currentStart = target - 1;
        currentEnd = currentStart + max;
        return nextStartPosition();
    }

    /**
     * Skip to the specified document (or the first document after it containing
     * hits).
     *
     * @param doc the doc number to skip to (or past)
     * @return true if we're still pointing to a valid hit, false if we're done
     */
    @Override
    public int advance(int doc) throws IOException {
        assert doc >= 0 && doc > docID();
        atFirstInCurrentDoc = false;
        if (currentDoc == NO_MORE_DOCS)
            return NO_MORE_DOCS;
        if (doc >= maxDoc) {
            currentDoc = NO_MORE_DOCS;
            currentStart = currentEnd = NO_MORE_POSITIONS;
            return NO_MORE_DOCS;
        }

        if (currentDoc >= doc) {
            // We can't skip to it because we're already there or beyond.
            // But, as per spec, advance always at least advances to the next document.
            return nextDoc();
        }

        // Advance to first livedoc containing matches at or after requested docID
        currentDoc = doc - 1;
        nextDoc();
        return currentDoc;
    }

    /**
     * @return start of current span
     */
    @Override
    public int startPosition() {
        if (atFirstInCurrentDoc)
            return -1; // .nextStartPosition() not called yet by client
        return currentStart;
    }

    @Override
    public String toString() {
        return "NGRAMS(" + min + ", " + max + ")";
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        // no clause, nothing to do
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        // no clause, no groups
    }

    @Override
    public boolean hasMatchInfo() {
        return false;
    }

    @Override
    public RelationInfo getRelationInfo() {
        return null;
    }

    @Override
    public int width() {
        return 0;
    }

    @Override
    public void collect(SpanCollector collector) {
        // nothing to collect
    }

    @Override
    public float positionsCost() {
        // we have no clause to derive this from; we should really return a value indicating
        // how many n-grams are in the entire corpus?
        // For now we just return a random value that seems fairly high, but maybe look into this more
        return 10_000;
    }

    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        // We have no inner clause and therefore no fast approximation we can use to skip documents.
        return null;
    }

}
