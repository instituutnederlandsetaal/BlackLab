package nl.inl.blacklab.codec;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Quickly look up which LeafReader a docId occurs in.
 *
 * Used by the global ForwardIndex and ContentStore implementations.
 */
public class LeafReaderLookupArray implements LeafReaderLookup {

    /** Segment for each doc id  */
    private final ObjectArrayList<LeafReaderContext> docIdToSegment;

    public LeafReaderLookupArray(IndexReader reader) {
        int maxDoc = 0;
        for (LeafReaderContext lrc : reader.leaves()) {
            maxDoc += lrc.reader().maxDoc();
        }
        // Store the segment for each doc id.
        this.docIdToSegment = new ObjectArrayList<>(maxDoc);
        for (LeafReaderContext lrc : reader.leaves()) {
            assert docIdToSegment.size() == lrc.docBase;
            for (int i = 0; i < lrc.reader().maxDoc(); i++) {
                docIdToSegment.add(lrc);
            }
        }
    }

    /**
     * Find the segment a given id occurs in.
     *
     * @param id (global) docId we're looking for
     * @return matching leafReaderContext, which gives us the leaf reader and docBase
     */
    public LeafReaderContext forId(int id) {
        if (id < 0 || id >= docIdToSegment.size()) {
            throw new IndexOutOfBoundsException("Doc ID " + id + " is out of bounds (max: " + (docIdToSegment.size() - 1) + ")");
        }
        return docIdToSegment.get(id);
    }
}
