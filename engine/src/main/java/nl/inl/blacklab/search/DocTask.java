package nl.inl.blacklab.search;

import org.apache.lucene.index.LeafReaderContext;

/** A task to perform on a Lucene document. */
public interface DocTask {

    /** Called when a new segment is started.
     * You need the segment's docBase if you want to convert a segment-local document ID
     * to a global document ID.
     */
    default void startSegment(LeafReaderContext segment) {}

    /**
     * Process a document.
     * <p>
     * Document is guaranteed to be live (not deleted).
     * <p>
     * To convert the segment-local document ID to a global document ID,
     * just add the segment's docBase to it.
     *
     * @param segmentDocId the document ID within the current segment.
     */
    void document(LeafReaderContext segment, int segmentDocId);
}
