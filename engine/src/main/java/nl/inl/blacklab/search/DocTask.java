package nl.inl.blacklab.search;

import org.apache.lucene.index.LeafReaderContext;

/** A task to perform on a Lucene document. */
public interface DocTask {

    /** Called before any documents have been processed.
     * @param index index we're processing
     */
    default void initializeTask(BlackLabIndex index) {}

    /** Called after all documents have been processed.
     * @param index index we're processing
     */
    default void finishTask(BlackLabIndex index) {}

    /**
     * Called before starting a new segment.
     *
     * Could be used to e.g. retrieve DocValues.
     *
     * @param segment segment we're going to process
     */
    default void startSegment(LeafReaderContext segment) {}

    /** Is this DocTask thread-safe?
     *
     * If yes, it may be run on multiple index segments in parallel.
     * Note that a single segment will always run in a single thread.
     */
    default boolean isThreadSafe() { return false; }

    /**
     * Process a document.
     * <p>
     * Document is guaranteed to be live (not deleted).
     * <p>
     * To convert the segment-local document ID to a global document ID,
     * just add the segment's docBase to it.
     *
     * @param segment segment we're processing
     * @param segmentDocId the document ID within the current segment.
     */
    void document(LeafReaderContext segment, int segmentDocId);
}
