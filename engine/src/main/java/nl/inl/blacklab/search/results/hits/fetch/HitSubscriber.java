package nl.inl.blacklab.search.results.hits.fetch;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.results.hits.Hits;

/**
 * Subscriber to a HitPublisher.
 * <p>
 * Receives hits in batches, and can indicate whether it needs more hits or
 * can pause for a while. Also receives counts after the maximum number of hits
 * has been reached.
 * <p>
 * Note that the subscriber must be allowed to store the Hits object it receives.
 * They must therfore be thread-safe. HitsFromPublishers uses them for its global
 * view.
 */
public interface HitSubscriber {

    /**
     * Called directly after subscribing.
     *
     * In HitsFromPublishers, the results object is stored permanently so we can use these objects
     * from several publishers to provide a global view on the combined hits.
     *
     * NOTE: some publishers (such as for grouping without storing hits) don't save their hits, to
     * save time and memory. These publishers will pass null for the results parameter. You should ensure
     * that publishers and subscribers are matched, so a subscriber needing access to all published hits
     * isn't subscribing to a publisher that doesn't save its hits.
     *
     * @param lrc our segment
     * @param results a persistent, locking Hits object containing all the hits published so far;
     *                whenever hits() is being called, that batch of hits is already included in this object.
     */
    void start(LeafReaderContext lrc, Hits results);

    /**
     * Do we need more hits right now, or is it okay to pause?
     * @return true if we need more hits
     */
    boolean needsMoreHits();

    /**
     * Called when there are hits for the subscriber to consume.
     * <p>
     * These batchHits should never contain "partial documents"; that is,
     * we're at a document boundary whenever this is called. (This assumes
     * that the source hits didn't have document hits mixed up)
     * The collector should collect the hits from batchStart up to,
     * but not including, batchEnd.
     *
     * Note that batchHits is not necessarily the full set of hits that have been
     * collected, but may just be a small batch of hits. batchOffsetInTotal indicates
     * what the index of batchStart in batchHits is in the full set of hits. So
     * if 100 hits have been published before, and this method gets called with
     * batchStart=0, batchEnd=10, batchOffsetInTotal=100, the batchHits 0 to 10 correspond to
     * hits 100 through 109 (inclusive) in the full set of hits from the publisher.
     * This ensures that we can pass nonlocking hit batchHits objects instead of the
     * locking full set of hits.
     *
     * @param lrc          segment these hits are from, or null if global
     * @param batchHits    where to consume hits from
     * @param batchStart   first hit to consume from batchHits
     * @param batchEnd     one past the last hit to consume from batchHits
     * @param batchNumDocs number of documents in the hits from batchStart to batchEnd
     * @param batchOffsetInTotal offset of batchStart[0] in this publisher's total set of hits
     */
    void hits(LeafReaderContext lrc, Hits batchHits, long batchStart, long batchEnd, int batchNumDocs, long batchOffsetInTotal);

    /**
     * Called after we hit the maximum number to process.
     * Informs the subscriber of some non-processed hits that have been counted.
     * Will be called until done() is called (because there are no more hits,
     * or we hit the maximum to count)
     *
     * @param hitsCounted number of hits counted that were not sent via hits()
     * @param docsCounted number of documents counted that were not sent via hits()
     */
    void counted(long hitsCounted, int docsCounted);

    /**
     * Called when the SpansReader is pausing or is done.
     * <p>
     * Implementations should make sure all hits published so far
     * have been processed.
     *
     * @param lrc          our segment
     * @param numPublished how many hits have been published by this publisher
     */
    void flush(LeafReaderContext lrc, long numPublished);

    /**
     * Called when all the hits have been collected.
     */
    void done(LeafReaderContext lrc);

    /**
     * Called when an exception is thrown while fetching hits.
     * @param lrc the segment we were processing when the error occurred, or null if global
     * @param exception the exception thrown
     */
    void error(LeafReaderContext lrc, Throwable exception);
}
