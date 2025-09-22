package nl.inl.blacklab.search.results.hits;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.results.hitresults.HitResults;

/**
 * A mutable list of hits, used internally.
 * <p>
 * Contrary to {@link HitResults}, this only contains doc, start and end
 * for each hit, so no captured groups information, and no other
 * bookkeeping (hit/doc retrieved/counted stats, etc.).
 */
public interface HitsMutable extends Hits {

    /**
     * Create an empty HitsInternal with an initial capacity.
     *
     * @param initialCapacity initial hits capacity, or default if negative
     * @param allowHugeLists if true, the object created can hold more than {@link Constants#JAVA_MAX_ARRAY_SIZE} hits
     * @param mustLock if true, return a locking implementation. If false, implementation may not be locking.
     * @return HitsInternal object
     */
    static HitsListAbstract create(Hits.HitsContext context,
            long initialCapacity, boolean allowHugeLists, boolean mustLock) {
        return create(context, initialCapacity, allowHugeLists ? Long.MAX_VALUE : Constants.JAVA_MAX_ARRAY_SIZE, mustLock);
    }

    /**
     * Create an empty HitsInternal with an initial and maximum capacity.
     *
     * Note that the maximum capacitiy simply determines which implementation is used;
     * it does not enforce a maximum size.
     *
     * @param initialCapacity initial hits capacity, or default if negative
     * @param mustLock if true, return a locking implementation. If false, implementation may not be locking.
     * @return HitsInternal object
     */
    static HitsListAbstract create(Hits.HitsContext context,
            long initialCapacity, long maxCapacity, boolean mustLock) {
        if (maxCapacity > Constants.JAVA_MAX_ARRAY_SIZE && BlackLab.config().getSearch().isEnableHugeResultSets()) {
            if (mustLock)
                return new HitsListLock(context, initialCapacity);
            return new HitsListNoLock(context, initialCapacity);
        }
        if (initialCapacity > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new UnsupportedOperationException("initialCapacity=" + initialCapacity + " > " + Constants.JAVA_MAX_ARRAY_SIZE + " && !allowHugeLists");
        if (mustLock)
            return new HitsListLock32(context, (int)initialCapacity);
        return new HitsListNoLock32(context, (int)initialCapacity);
    }

    default void add(Hits from, long startIndex, long endIndex) {
        if (startIndex == 0 && endIndex == from.size()) {
            // Adding all hits; more efficient to just add them all at once
            addAll(from);
            return;
        }
        EphemeralHit hit = new EphemeralHit();
        for (long i = startIndex; i < endIndex; i++) {
            from.getEphemeral(i, hit);
            add(hit);
        }
    }

    void add(int doc, int start, int end, MatchInfo[] matchInfo);

    void add(EphemeralHit hit);

    void addAll(Hits hits);

    /**
     * Remove all hits.
     */
    void clear();

    /**
     * Add segment hits to this global hits list.
     *
     * This adds the docBase to the document ids, to convert
     * the segment doc ids to global doc ids.
     *
     * @param segmentHits segment hits to add
     */
    default void addAllConvertDocBase(Hits segmentHits) {
        int docBase = segmentHits.context().leafReaderContext().docBase;
        for (EphemeralHit hit: segmentHits) {
            hit.convertDocIdToGlobal(docBase);
            add(hit);
        }
    }
}
