package nl.inl.blacklab.search.results;

import java.util.function.Consumer;

import org.apache.lucene.search.spans.Spans;

import it.unimi.dsi.fastutil.ints.IntIterator;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.lucene.MatchInfo;

/**
 * A list of simple hits.
 * <p>
 * Contrary to {@link Hits}, this only contains doc, start and end
 * for each hit, so no captured groups information, and no other
 * bookkeeping (hit/doc retrieved/counted stats, hasAscendingLuceneDocIds, etc.).
 * <p>
 * This is a read-only interface.
 */
public interface HitsInternal extends Iterable<EphemeralHit> {

    /** An empty HitsInternalRead object. */
    HitsInternal EMPTY_SINGLETON = new HitsInternalNoLock32();

    /**
     * Create an empty HitsInternal with an initial capacity.
     *
     * @param initialCapacity initial hits capacity, or default if negative
     * @param allowHugeLists if true, the object created can hold more than {@link Constants#JAVA_MAX_ARRAY_SIZE} hits
     * @param mustLock if true, return a locking implementation. If false, implementation may not be locking.
     * @return HitsInternal object
     */
    static HitsInternalMutable create(long initialCapacity, boolean allowHugeLists, boolean mustLock) {
        return create(initialCapacity, allowHugeLists ? Long.MAX_VALUE : Constants.JAVA_MAX_ARRAY_SIZE, mustLock);
    }

    static HitsInternalMutable create(long initialCapacity, long maxCapacity, boolean mustLock) {
        if (maxCapacity > Constants.JAVA_MAX_ARRAY_SIZE && BlackLab.config().getSearch().isEnableHugeResultSets()) {
            if (mustLock)
                return new HitsInternalLock(initialCapacity);
            return new HitsInternalNoLock(initialCapacity);
        }
        if (initialCapacity > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new UnsupportedOperationException("initialCapacity=" + initialCapacity + " > " + Constants.JAVA_MAX_ARRAY_SIZE + " && !allowHugeLists");
        if (mustLock)
            return new HitsInternalLock32((int)initialCapacity);
        return new HitsInternalNoLock32((int)initialCapacity);
    }

    /**
     * Perform an operation with read lock.
     * <p>
     * If the implementation doesn't support locking, it will simply
     * perform the operation without it.
     *
     * @param cons operation to perform
     */
    void withReadLock(Consumer<HitsInternal> cons);

    /**
     * Get a Hit object.
     * <p>
     * Avoid this method if possible, as it instantiates an object.
     *
     * @param index hit index
     * @return hit object
     */
    Hit get(long index);

    /**
     * Get an ephemeral Hit object.
     * <p>
     * Writes the doc, start and end values to the specified mutable
     * hit object.
     * <p>
     * Useful in a hot loop or somesuch.
     * The intent of this function is to allow retrieving many hits without needing to allocate so many short lived objects.
     * Example:
     *
     * <pre>
     * EphemeralHitImpl h = new EphemeralHitImpl();
     * int size = hits.size();
     * for (int i = 0; i < size; ++i) {
     *     hits.getEphemeral(i, h);
     *     // use h now
     * }
     * </pre>
     *
     * @param index hit index
     * @param h     hit object
     */
    void getEphemeral(long index, EphemeralHit h);

    /**
     * Get the doc id for a hit.
     *
     * @param index hit index
     * @return doc id
     */
    int doc(long index);

    /**
     * Get the start position for a hit.
     *
     * @param index hit index
     * @return start position
     */
    int start(long index);

    /**
     * Get the end position for a hit.
     *
     * @param index hit index
     * @return end position
     */
    int end(long index);

    /**
     * Get extra information for a match, such as captured groups and relations.
     * <p>
     * Only available if the query captures such information.
     *
     * @return extra information for a match, or null if none available
     */
    MatchInfo[] matchInfo(long index);

    long size();

    /**
     * Iterate over the doc ids of the hits.
     * <p>
     * NOTE: iterating does not lock the arrays, to do that,
     * it should be performed in a {@link #withReadLock} callback.
     *
     * @return iterator over the doc ids
     */
    IntIterator docsIterator();

    /**
     * Iterate over the hits.
     * <p>
     * NOTE: iterating does not lock the arrays, to do that,
     * it should be performed in a {@link #withReadLock} callback.
     *
     * @return iterator
     */
    @Override
    Iterator iterator();

    /**
     * Return a new object with sorted hits.
     *
     * @param p sort property
     * @return sorted hits
     */
    HitsInternal sort(HitProperty p);

    /**
     * For iterating through the hits using EphemeralHit
     */
    interface Iterator extends java.util.Iterator<EphemeralHit> {

    }

    static boolean debugCheckAllReasonable(HitsInternal hits) {
        for (EphemeralHit h: hits) {
            assert debugCheckReasonableHit(h);
        }
        return true;
    }

    static boolean debugCheckReasonableHit(Hit h) {
        return debugCheckReasonableHit(h.doc(), h.start(), h.end());
    }

    static boolean debugCheckReasonableHit(int doc, int start, int end) {
        assert doc >= 0 : "Hit doc id must be non-negative, is " + doc;
        assert doc != Spans.NO_MORE_DOCS : "Hit doc id must not equal NO_MORE_DOCS";
        assert start >= 0 : "Hit start must be non-negative, is " + start;
        assert end >= 0 : "Hit end must be non-negative, is " + start;
        assert start <= end : "Hit start " + start + " > end " + end;
        assert start != Spans.NO_MORE_POSITIONS : "Hit start must not equal NO_MORE_POSITIONS";
        assert end != Spans.NO_MORE_POSITIONS : "Hit end must not equal NO_MORE_POSITIONS";
        return true;
    }
}
