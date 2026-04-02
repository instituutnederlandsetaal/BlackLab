package nl.inl.blacklab.plugins;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.DocTask;

/** Performs a task on a document in a BlackLab index. */
public abstract class IndexDocTask extends Plugin implements DocTask {

    /** Called before any documents have been processed.
     * @param index index we're processing
     */
    @SuppressWarnings("RedundantMethodOverride")
    public void initializeTask(BlackLabIndex index) {}

    /** Called after all documents have been processed.
     * @param index index we're processing
     */
    @SuppressWarnings("RedundantMethodOverride")
    public void finishTask(BlackLabIndex index) {}

    /**
     * Called before starting a new segment.
     *
     * Could be used to e.g. retrieve DocValues.
     *
     * @param segment segment we're going to process
     */
    @SuppressWarnings("RedundantMethodOverride")
    public void startSegment(LeafReaderContext segment) {}

    /**
     * Process a document.
     * <p>
     * Document is guaranteed to be live (not deleted).
     * <p>
     * To convert the segment-local document ID to a global document ID,
     * just add the segment's docBase to it.
     *
     * @param segment segment the document is from
     * @param segmentDocId the document ID within the current segment.
     */
    public void document(LeafReaderContext segment, int segmentDocId) throws PluginException {
        throw new PluginException("Method not implemented");
    }
}
