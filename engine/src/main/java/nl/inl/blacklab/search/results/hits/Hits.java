package nl.inl.blacklab.search.results.hits;

import java.util.Iterator;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.hitresults.Concordances;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hitresults.Kwics;

/**
 * A list of simple hits.
 * <p>
 * Contrary to {@link HitResults}, this only contains doc, start and end
 * for each hit, so no captured groups information, and no other
 * bookkeeping (hit/doc retrieved/counted stats, etc.).
 * <p>
 * This is a read-only interface.
 */
public interface Hits extends Iterable<EphemeralHit> {

    /** An empty list of hits. */
    static Hits empty(AnnotatedField field, MatchInfoDefs matchInfoDefs) {
        return new HitsListNoLock32(field, matchInfoDefs, -1);
    }

    static Hits fromLists(AnnotatedField field,
            int[] docs, int[] starts, int[] ends) {
        IntList lDocs = new IntArrayList(docs);
        IntList lStarts = new IntArrayList(starts);
        IntList lEnds = new IntArrayList(ends);
        return new HitsListNoLock32(field, null, lDocs, lStarts, lEnds, null);
    }

    static Hits single(AnnotatedField field, MatchInfoDefs matchInfoDefs, int doc, int matchStart, int matchEnd) {
        if (doc < 0 || matchStart < 0 || matchEnd < 0 || matchStart > matchEnd) {
            throw new IllegalArgumentException("Invalid hit: doc=" + doc + ", start=" + matchStart + ", end=" + matchEnd);
        }
        HitsMutable hits = HitsMutable.create(field, matchInfoDefs, 1, false, false);
        hits.add(doc, matchStart, matchEnd, null);
        return hits;
    }

    /**
     * Get the field these hits are from.
     *
     * @return field
     */
    AnnotatedField field();

    default BlackLabIndex index() {
        return field().index();
    }

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
    Hits getStatic();

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
    Hits sublist(long first, long windowSize);

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
    boolean isEmpty();

    /**
     * Return a new hits object with these hits sorted by the given property.
     *
     * @param sortProp the hit property to sort on
     * @return a new hits object with the same hits, sorted in the specified way
     */
    Hits sorted(HitProperty sortProp);

    default Map<PropertyValue, Group> grouped(HitProperty groupBy) {
        return grouped(groupBy, Long.MAX_VALUE);
    }

    Map<PropertyValue, Group> grouped(HitProperty groupBy, long maxResultsToStorePerGroup);

    long countDocs();

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

    /**
     * Create concordances.
     *
     * @param contextSize desired context size
     * @param type concordance type: from forward index or original content
     * @return concordances
     */
    Concordances concordances(ContextSize contextSize, ConcordanceType type);

    Kwics kwics(ContextSize contextSize);

    Hits filteredByDocId(int docId);

    /** Used during the actual grouping */
    class Group {

        HitsMutable storedHits;

        long totalNumberOfHits;

        public Group(HitsMutable storedHits, int totalNumberOfHits) {
            this.storedHits = storedHits;
            this.totalNumberOfHits = totalNumberOfHits;
        }

        public HitsMutable getStoredHits() {
            return storedHits;
        }

        public long getTotalNumberOfHits() {
            return totalNumberOfHits;
        }

    }
}
