package nl.inl.blacklab.search.results.hits;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/**
 * A mutable list of simple hits, used internally.
 * <p>
 * Contrary to {@link Hits}, this only contains doc, start and end
 * for each hit, so no captured groups information, and no other
 * bookkeeping (hit/doc retrieved/counted stats, etc.).
 */
public interface HitsInternalMutable extends HitsSimple {

    /**
     * Create an empty HitsInternal with an initial capacity.
     *
     * @param initialCapacity initial hits capacity, or default if negative
     * @param allowHugeLists if true, the object created can hold more than {@link Constants#JAVA_MAX_ARRAY_SIZE} hits
     * @param mustLock if true, return a locking implementation. If false, implementation may not be locking.
     * @return HitsInternal object
     */
    static HitsInternalMutable create(AnnotatedField field, MatchInfoDefs matchInfoDefs, long initialCapacity,
            boolean allowHugeLists, boolean mustLock) {
        return create(field, matchInfoDefs, initialCapacity, allowHugeLists ? Long.MAX_VALUE : Constants.JAVA_MAX_ARRAY_SIZE, mustLock);
    }

    static HitsInternalMutable create(AnnotatedField field, MatchInfoDefs matchInfoDefs, long initialCapacity,
            long maxCapacity, boolean mustLock) {
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

    void add(int doc, int start, int end, MatchInfo[] matchInfo);

    void add(EphemeralHit hit);

    void add(Hit hit);

    void addAll(HitsSimple hits);

    /**
     * Remove all hits.
     */
    void clear();

    void setMatchInfoDefs(MatchInfoDefs matchInfoDefs);

}
