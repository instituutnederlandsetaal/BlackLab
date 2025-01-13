package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.Spans;

/**
 * Wrap a Spans to retrieve matches per document, so we can process all matches
 * in a document efficiently.
 *
 * This way we can retrieve hits per document and perform some operation on them
 * (like sorting or retrieving some extra information). Afterwards we can use
 * HitsPerDocumentSpans to convert the per-document hits into a normal Spans
 * object again.
 */
class SpansInBucketsPerDocument extends SpansInBucketsAbstract {

    public static SpansInBucketsPerDocument sorted(BLSpans spansFilter) {
        if (spansFilter.guarantees().hitsStartPointSorted()) {
            // Already start point sorted; no need to sort buckets again
            return new SpansInBucketsPerDocument(spansFilter);
        }
        // Not sorted yet; sort buckets
        return new SpansInBucketsPerDocumentSorted(spansFilter, true);
    }

    protected class PerDocBucket extends AbstractBucket {
        @Override
        public void gatherHits() throws IOException {
            assert(source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS);
            do {
                addHitFromSource();
            } while (source.nextStartPosition() != Spans.NO_MORE_POSITIONS);
            assert source.startPosition() == Spans.NO_MORE_POSITIONS;
        }
    }

    public SpansInBucketsPerDocument(BLSpans source) {
        super(source);
        setBucket(new PerDocBucket());
    }

    @Override
    public String toString() {
        return "SIB-DOC(" + source + ")";
    }
}
