package nl.inl.blacklab.search.results;

import java.util.Iterator;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/** A minimal Hits/HitsInternal interface that is all that HitProperty needs. */
public interface HitsSimple {

    /**
     * Get the field these hits are from.
     *
     * @return field
     */
    AnnotatedField field();

    BlackLabIndex index();

    /**
     * Type of each of our match infos.
     *
     * @return list of match info definitions
     */
    MatchInfoDefs matchInfoDefs();

    /**
     * Get the number of hits.
     *
     * @return number of hits
     */
    long size();

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

    MatchInfo[] matchInfos(long hitIndex);

    MatchInfo matchInfo(long hitIndex, int matchInfoIndex);

    /**
     * Get the most efficient interface to these Hits.
     *
     * Most efficient means that it will return a non-locking
     * object with direct access to the hits lists.
     *
     * Hits instances will typically wait until all hits are fetched
     * (if applicable), then return their internal hits object.
     *
     * HitsInternal instances will return themselves (if it's non-locking),
     * or a non-locking version of the same hits (if it's a locking instance).
     *
     * CAUTION: make sure any other threads are done modifying this object
     * before calling this method!
     *
     * @return internal hits object.
     */
    HitsSimple getFinishedHits();

    /**
     * Get a sublist of hits, starting at the specified index.
     *
     * If first + windowSize is larger than the number of hits,
     * the sublist returned will be smaller than windowSize.
     *
     * @param first first hit in the sublist (0-based)
     * @param windowSize size of the sublist
     * @return sublist of hits
     */
    HitsSimple sublist(long first, long windowSize);

    Iterator<EphemeralHit> ephemeralIterator();

    /**
     * Check if this hits object is empty.
     *
     * @return true if there are no hits, false otherwise
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Return a new hits object with these hits sorted by the given property.
     *
     * @param sortProp the hit property to sort on
     * @return a new hits object with the same hits, sorted in the specified way
     */
    HitsSimple sorted(HitProperty sortProp);
}
