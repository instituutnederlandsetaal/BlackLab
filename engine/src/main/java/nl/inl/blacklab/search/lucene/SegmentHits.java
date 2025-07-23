package nl.inl.blacklab.search.lucene;

import nl.inl.blacklab.search.results.EphemeralHit;

/** For iterating through the hits from a single segment.
 *
 * Subclasses may or may not perform some operation on the hits,
 * e.g. sorting.
 *
 * Note that unlike the Spans interface, docId is not guaranteed to
 * be in order, and hits from the same document may not be consecutive.
 */
public interface SegmentHits {

    /**
     * Get the next hit from this segment.
     *
     * Waits until the next hit is available if necessary.
     *
     * @param h will be filled with the next hit
     * @return true if there is a next hit, false if there are no more hits
     */
    boolean nextHit(EphemeralHit h);

}
