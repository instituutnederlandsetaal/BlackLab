package nl.inl.blacklab.search.results.hits;

import java.util.Iterator;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/** A minimal Hits/HitsInternal interface that is all that HitProperty needs. */
public interface HitsSimple extends Iterable<EphemeralHit> {

    /** An empty list of hits. */
    static HitsSimple empty(AnnotatedField field, MatchInfoDefs matchInfoDefs) {
        return new HitsInternalNoLock32(field, matchInfoDefs, -1);
    }

    static HitsSimple fromLists(AnnotatedField field,
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
        HitsInternalMutable hits = HitsInternalMutable.create(field, matchInfoDefs, 1, false, false);
        hits.add(doc, matchStart, matchEnd, null);
        return hits;
    }

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
     * @param index index of the desired hit
     * @return the hit, or null if it's beyond the last hit
     */
    Hit get(long index);

    /**
     * Copy hit information into a temporary object.
     *
     * @param index index of the desired hit
     * @param hit object to copy values to
     */
    void getEphemeral(long index, EphemeralHit hit);

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
    HitsSimple getStatic();

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

    /**
     * Get an iterator over the hits in this Hits object.
     * <p>
     * The iterator is not thread-safe.
     * <p>
     * It will return an EphemeralHit object for each hit, which is temporary
     * and should not be retained.
     *
     * @return iterator over the hits in this Hits object
     */
    Iterator<EphemeralHit> iterator();

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

    boolean hasMatchInfo();

    /**
     * Create concordances from the forward index.
     *
     * @param contextSize desired context size
     * @return concordances
     */
    default Concordances concordances(ContextSize contextSize) {
        return concordances(contextSize, ConcordanceType.FORWARD_INDEX);
    }

    default Concordances concordances(ContextSize contextSize, ConcordanceType type) {
        if (contextSize == null)
            contextSize = index().defaultContextSize();
        if (type == null)
            type = ConcordanceType.FORWARD_INDEX;
        return new Concordances(getStatic(), type, contextSize);
    }

    default Kwics kwics(ContextSize contextSize) {
        if (contextSize == null)
            contextSize = index().defaultContextSize();
        return new Kwics(getStatic(), contextSize);
    }

    default HitsSimple filteredByDocId(int docId) {
        HitsInternalMutable hitsInDoc = HitsInternalMutable.create(field(), matchInfoDefs(), -1, size(), false);
        for (EphemeralHit h: this) {
            if (h.doc() == docId)
                hitsInDoc.add(h);
        }
        return hitsInDoc;
    }
}
