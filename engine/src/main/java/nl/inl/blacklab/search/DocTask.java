package nl.inl.blacklab.search;

import org.apache.lucene.index.LeafReaderContext;

/** A task to perform on a Lucene document. */
public interface DocTask {

    /** Called before any documents have been processed.
     */
    default void initializeTask() {}

    /** Called after all documents have been processed.
     */
    default void finishTask() {}

    /** Is this DocTask thread-safe?
     *
     * If yes, it may be run on multiple index segments in parallel.
     * Note that a single segment will always run in a single thread.
     */
    default boolean isThreadSafe() { return false; }

    /** Instantiate segment doc task */
    SegmentTask segmentDocTask(LeafReaderContext segment);

    /** Runs doc task on a single segment.
     *
     * This allows us to keep segment-specific information (e.g. DocValues)
     * in the object without having to do a lookup for each document.
     */
    interface SegmentTask {

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
        void document(int segmentDocId);
    }
}
