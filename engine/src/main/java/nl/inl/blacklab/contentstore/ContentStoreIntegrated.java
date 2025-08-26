package nl.inl.blacklab.contentstore;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.BlackLabPostingsReader;
import nl.inl.blacklab.codec.LeafReaderLookup;

/**
 * Global content store interface for the integrated index format.
 *
 * Will forward any requests to the appropriate {@link ContentStoreSegmentReader}.
 */
public class ContentStoreIntegrated implements ContentStore {

    /** Quickly look up the segment a document is stored in */
    private final LeafReaderLookup leafReaderLookup;

    /** Field we're accessing */
    private final String luceneField;

    public ContentStoreIntegrated(LeafReaderLookup leafReaderLookup, String luceneField) {
        if (leafReaderLookup == null)
            throw new IllegalArgumentException("LeafReaderLookup cannot be null");
        if (luceneField == null || luceneField.isEmpty())
            throw new IllegalArgumentException("Lucene field cannot be null or empty");
        this.leafReaderLookup = leafReaderLookup;
        this.luceneField = luceneField;
    }

    /**
     * Get the content store for an index segment.
     *
     * The returned content store should only be used from one thread.
     *
     * @param lrc leafreader context (segment) to get the content store for.
     * @return content store
     */
    static ContentStoreSegmentReader contentStore(LeafReaderContext lrc) {
        return BlackLabPostingsReader.forSegment(lrc).getStoredFieldsReader().contentStore();
    }

    @Override
    public String[] retrieveParts(int docId, int[] start, int[] end) {
        for (int i = 0; i < start.length; i++)
            if (start[i] == -1)  start[i] = 0; // fix legacy quirk
        LeafReaderContext lrc = leafReaderLookup.forId(docId);
        ContentStoreSegmentReader cs = contentStore(lrc);
        return cs.getValueSubstrings(docId - lrc.docBase, luceneField, start, end);
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
