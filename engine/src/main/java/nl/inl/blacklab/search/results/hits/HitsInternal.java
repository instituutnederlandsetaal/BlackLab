package nl.inl.blacklab.search.results.hits;

import java.util.Iterator;
import java.util.function.Consumer;

import org.apache.lucene.queries.spans.Spans;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/**
 * A list of simple hits.
 * <p>
 * Contrary to {@link Hits}, this only contains doc, start and end
 * for each hit, so no captured groups information, and no other
 * bookkeeping (hit/doc retrieved/counted stats, etc.).
 * <p>
 * This is a read-only interface.
 */
public interface HitsInternal extends Iterable<EphemeralHit>, HitsSimple {

    /** An empty list of hits. */
    static HitsInternalMutable empty(AnnotatedField field, MatchInfoDefs matchInfoDefs) {
        return new HitsInternalNoLock32(field, matchInfoDefs, -1);
    }

    /**
     * Create an empty HitsInternal with an initial capacity.
     *
     * @param initialCapacity initial hits capacity, or default if negative
     * @param allowHugeLists if true, the object created can hold more than {@link Constants#JAVA_MAX_ARRAY_SIZE} hits
     * @param mustLock if true, return a locking implementation. If false, implementation may not be locking.
     * @return HitsInternal object
     */
    static HitsInternalMutable create(AnnotatedField field, MatchInfoDefs matchInfoDefs, long initialCapacity, boolean allowHugeLists, boolean mustLock) {
        return create(field, matchInfoDefs, initialCapacity, allowHugeLists ? Long.MAX_VALUE : Constants.JAVA_MAX_ARRAY_SIZE, mustLock);
    }

    static HitsInternalMutable create(AnnotatedField field, MatchInfoDefs matchInfoDefs, long initialCapacity, long maxCapacity, boolean mustLock) {
        if (maxCapacity > Constants.JAVA_MAX_ARRAY_SIZE && BlackLab.config().getSearch().isEnableHugeResultSets()) {
            if (mustLock)
                return new HitsInternalLock(field, matchInfoDefs, initialCapacity);
            return new HitsInternalNoLock(field, matchInfoDefs, initialCapacity);
        }
        if (initialCapacity > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new UnsupportedOperationException("initialCapacity=" + initialCapacity + " > " + Constants.JAVA_MAX_ARRAY_SIZE + " && !allowHugeLists");
        if (mustLock)
            return new HitsInternalLock32(field, matchInfoDefs, (int)initialCapacity);
        return new HitsInternalNoLock32(field, matchInfoDefs, (int)initialCapacity);
    }

    static HitsInternalMutable fromLists(AnnotatedField field,
            int[] docs, int[] starts, int[] ends) {
        IntList lDocs = new IntArrayList(docs);
        IntList lStarts = new IntArrayList(starts);
        IntList lEnds = new IntArrayList(ends);
        return new HitsInternalNoLock32(field, null, lDocs, lStarts, lEnds, null);
    }

    static HitsSimple single(AnnotatedField field, MatchInfoDefs matchInfoDefs, int doc, int matchStart, int matchEnd) {
        if (doc < 0 || matchStart < 0 || matchEnd < 0 || matchStart > matchEnd) {
            throw new IllegalArgumentException("Invalid hit: doc=" + doc + ", start=" + matchStart + ", end=" + matchEnd);
        }
        HitsInternalMutable hits = create(field, matchInfoDefs, 1, false, false);
        hits.add(doc, matchStart, matchEnd, null);
        return hits;
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
     * Iterate over the hits.
     * <p>
     * NOTE: iterating does not lock the arrays, to do that,
     * it should be performed in a {@link #withReadLock} callback.
     *
     * @return iterator
     */
    @Override
    Iterator<EphemeralHit> iterator();

    @Override
    default Iterator<EphemeralHit> ephemeralIterator() {
        return iterator();
    }

    /**
     * Return a new object with sorted hits.
     *
     * @param p sort property
     * @return sorted hits
     */
    HitsSimple sorted(HitProperty p);

    @Override
    default HitsSimple getStatic() {
        return this;
    }

    /**
     * Return a non-locking version of this HitsInternal.
     *
     * CAUTION: this will use the same lists as this HitsInternal,
     * it just won't use any locking. Make sure no locking is required
     * anymore (for example, because all the hits have been added).
     */
    default HitsSimple nonlocking() {
        return this;
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
