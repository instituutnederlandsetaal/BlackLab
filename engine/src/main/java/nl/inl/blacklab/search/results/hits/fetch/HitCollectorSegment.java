package nl.inl.blacklab.search.results.hits.fetch;

import nl.inl.blacklab.search.results.hits.Hits;

/**
 * Deals with hits found in a segment. Works with HitCollector.
 *
 * Implementations don't need to be thread-safe, as each segment is handled by a single thread.
 */
public interface HitCollectorSegment {
    /**
     * Called when there are hits for the collector to collect.
     *
     * These results should never contain "partial documents"; that is,
     * we're at a document boundary whenever this is called. (This assumes
     * that the source hits didn't have document hits mixed up)
     *
     * @param results the hits collected so far
     * @return whether to continue storing hits, or just count them, or stop altogether
     */
    void collect(Hits results);

    /**
     * Called when the SpansReader is pausing or is done.
     *
     * The HitCollector should make sure all hits collected so far are processed.
     */
    void flush();

}
