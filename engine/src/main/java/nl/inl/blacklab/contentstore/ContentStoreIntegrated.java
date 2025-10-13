package nl.inl.blacklab.contentstore;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.LeafReaderLookup;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;

/**
 * Global content store interface for the integrated index format.
 *
 * Will forward any requests to the appropriate {@link ContentStoreSegmentReader}.
 */
public class ContentStoreIntegrated implements ContentStore {

    public static ContentStore open(IndexReader reader, String luceneField) {
        return new ContentStoreIntegrated(reader, luceneField);
    }

    /** Quickly look up the segment a document is stored in */
    private final LeafReaderLookup leafReaderLookup;

    /** Field we're accessing */
    private final String luceneField;

    public ContentStoreIntegrated(IndexReader reader, String luceneField) {
        leafReaderLookup = new LeafReaderLookup(reader);
        this.luceneField = luceneField;
    }

    @Override
    public String retrieve(int docId) {
        LeafReaderContext lrc = leafReaderLookup.forId(docId);
        ContentStoreSegmentReader cs = BlackLabIndexIntegrated.contentStore(lrc);
        return cs.getValue(docId - lrc.docBase, luceneField);
    }

    @Override
    public String retrievePart(int docId, int start, int end) {
        if (start == -1) start = 0; // fix legacy quirk
        LeafReaderContext lrc = leafReaderLookup.forId(docId);
        ContentStoreSegmentReader cs = BlackLabIndexIntegrated.contentStore(lrc);
        return cs.getValueSubstring(docId - lrc.docBase, luceneField, start, end);
    }

    @Override
    public String[] retrieveParts(int docId, int[] start, int[] end) {
        for (int i = 0; i < start.length; i++)
            if (start[i] == -1)  start[i] = 0; // fix legacy quirk
        LeafReaderContext lrc = leafReaderLookup.forId(docId);
        ContentStoreSegmentReader cs = BlackLabIndexIntegrated.contentStore(lrc);
        return cs.getValueSubstrings(docId - lrc.docBase, luceneField, start, end);
    }

    @Override
    public int docLength(int docId) {
        LeafReaderContext lrc = leafReaderLookup.forId(docId);
        ContentStoreSegmentReader cs = BlackLabIndexIntegrated.contentStore(lrc);
        return cs.valueLength(docId - lrc.docBase, luceneField);
    }

    @Override
    public void initialize() {
        // nothing to do here
    }

    @Override
    public void close() {
        // nothing to do here
    }
}
