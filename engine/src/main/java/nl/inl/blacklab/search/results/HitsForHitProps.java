package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/** A minimal Hits/HitsInternal interface that is all that HitProperty needs. */
public interface HitsForHitProps {

    /**
     * Get the field these hits are from.
     *
     * @return field
     */
    AnnotatedField field();

    /**
     * Type of each of our match infos.
     *
     * @return list of match info definitions
     */
    MatchInfoDefs matchInfoDefs();

    /**
     * Return the specified hit.
     * Implementations of this method should be thread-safe.
     *
     * @param i index of the desired hit
     * @return the hit, or null if it's beyond the last hit
     */
    Hit get(long i);

    /**
     * Copy hit information into a temporary object.
     *
     * @param i index of the desired hit
     * @param hit object to copy values to
     */
    void getEphemeral(long i, EphemeralHit hit);

    /**
     * Get Lucene document id for the specified hit
     *
     * @param index hit index
     * @return document id
     */
    int doc(long index);

    /**
     * Get start position for the specified hit
     *
     * @param index hit index
     * @return document id
     */
    int start(long index);

    /**
     * Get end position for the specified hit
     *
     * @param index hit index
     * @return document id
     */
    int end(long index);

    /**
     * Get the internal hits object.
     *
     * CAUTION: only use this if you know what you're doing!
     *
     * @return internal hits object.
     */
    HitsInternal getInternalHits();

    BlackLabIndex index();
}
