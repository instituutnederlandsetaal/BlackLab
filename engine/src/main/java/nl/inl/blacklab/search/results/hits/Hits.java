package nl.inl.blacklab.search.results.hits;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReaderContext;

import com.ibm.icu.text.CollationKey;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropContext;
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

    Logger logger = LogManager.getLogger(Hits.class);

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
        return new HitsSingle(field, matchInfoDefs, doc, matchStart, matchEnd);
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
     * Depending on the implementation, this may lock until enough hits
     * have been fetched.
     *
     * @return number of hits
     */
    long size();

    /**
     * Check if this hits object has at least the specified number of hits.
     *
     * Depending on the implementation, this may lock until enough hits
     * have been fetched.
     *
     * @param minSize minimum number of hits required
     * @return true if there are at least minSize hits, false otherwise
     */
    boolean sizeAtLeast(long minSize);

    /**
     * Check if this hits object is empty.
     *
     * Depending on the implementation, this may lock until it
     * knows whether there are any hits or not.
     *
     * @return true if there are no hits, false otherwise
     */
    boolean isEmpty();

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
     * Return an iterator over the hits in this Hits object that are in the specified segment.
     *
     * NOTE: returns the hits unchanged, so does NOT subtract docBase from the document ids!
     *
     * @param lrc the LeafReaderContext for the segment to iterate over
     * @return iterator over the segment hits
     */
    default Iterator<EphemeralHit> segmentIterator(LeafReaderContext lrc) {
        return new Iterator<>() {

            long nextHit = -1; // "not started yet"

            EphemeralHit hit = new EphemeralHit();

            {
                // Find first hit
                findNextHit();
            }

            @Override
            public boolean hasNext() {
                return nextHit < size();
            }

            @Override
            public EphemeralHit next() {
                if (nextHit >= size())
                    throw new IndexOutOfBoundsException("No more hits available");
                getEphemeral(nextHit, hit);
                findNextHit();
                return hit;
            }

            private void findNextHit() {
                nextHit++;
                while (nextHit < size()) {
                    if (doc(nextHit) >= lrc.docBase && doc(nextHit) < lrc.docBase + lrc.reader().maxDoc()) {
                        // Found a hit in this segment
                        return;
                    }
                    nextHit++;
                }
            }
        };
    }

    /**
     * Return a new hits object with these hits sorted by the given property.
     *
     * @param sortBy the hit property to sort on
     * @return a new hits object with the same hits, sorted in the specified way
     */
    default Hits sorted(HitProperty sortBy) {
        // Fetch all the hits and determine size.
        long n = size();
        HitsListAbstract mergedHits = HitsMutable.create(field(), matchInfoDefs(),
                n, n, false);
        Map<LeafReaderContext, Hits> perSegment = hitsPerSegment();
        if (perSegment != null) {
            // Use per-segment hits directly rather than through a global view
            for (Map.Entry<LeafReaderContext, Hits> segmentHits: perSegment.entrySet()) {
                Hits hits = segmentHits.getValue().getStatic();
                mergedHits.addAllConvertDocBase(hits, segmentHits.getKey().docBase);
            }
        } else {
            // Just copy all hits and sort them.
            // (subclass will usually override this method to do it more efficiently)
            mergedHits.addAllConvertDocBase(getStatic(), 0);
        }
        HitProperty sortByWithContext = sortBy.copyWith(PropContext.globalHits(mergedHits,
                new ConcurrentHashMap<>()));
        // NOTE: We're calling HitsListAbstract.sorted(), not recursing endlessly.
        return mergedHits.sorted(sortByWithContext);
    }

    /**
     * Filter hits using the given function.
     *
     * PROBLEM: we want to filter lazily, using a similar approach to HitsFromQuery.
     *
     * @param property property to filter on
     * @param value value to filter with
     * @param mustMaintainOrder if true, the returned hits must be in the same order as the original hits
     * @return filtered hits
     */
    default Hits filtered(HitProperty property, PropertyValue value, boolean mustMaintainOrder) {
        // Fetch all the hits and determine size.
        long totalSourceHits = size();
        Map<LeafReaderContext, Hits> perSegment = hitsPerSegment();
        if (perSegment != null && !mustMaintainOrder) {
            // Use per-segment hits directly rather than through a global view
            int numThreads = Math.min(
                    Math.max(index().blackLab().maxThreadsPerSearch(), 1),
                    HitsUtils.IDEAL_NUM_THREADS_GROUPING);
            Parallel<Map.Entry<LeafReaderContext, Hits>, HitsMutable> parallel = new Parallel<>(index(), numThreads);
            Map<String, CollationKey> collationCache = new ConcurrentHashMap<>();
            return parallel.mapReduce(perSegment.entrySet(),
                    entry -> entry.getValue().size(),
                    threadItems -> {
                        // Group items in these segments into a single map.
                        long numHits = threadItems.stream().map(e -> e.getValue().size()).reduce(0L, Long::sum);
                        HitsMutable filteredHits = HitsMutable.create(field(), matchInfoDefs(),
                                numHits, numHits, false);
                        for (Map.Entry<LeafReaderContext, Hits> entry: threadItems) {
                            Hits hits = entry.getValue().getStatic();
                            LeafReaderContext lrc = entry.getKey();
                            HitProperty segProperty = property.copyWith(PropContext.segmentHits(hits, lrc, collationCache));
                            for (long i = 0; i < hits.size(); i++) {
                                if (segProperty.get(i).equals(value)) {
                                    // This hit matches the filter, add it to the results
                                    EphemeralHit hit = new EphemeralHit();
                                    hits.getEphemeral(i, hit);
                                    hit.convertDocIdToGlobal(lrc.docBase);
                                    filteredHits.add(hit);
                                }
                            }
                        }
                        return List.of(filteredHits);
                    },
                    HitsMutable::addAll,
                    () -> HitsMutable.create(field(), matchInfoDefs(), -1,
                            totalSourceHits, false)
            );
        } else {
            // Just filter the hits sequentially.
            // (subclass could override this method to do it more efficiently)
            HitProperty globalProperty = property.copyWith(PropContext.globalHits(this, new ConcurrentHashMap<>()));
            HitsListAbstract allFilteredHits = HitsMutable.create(field(), matchInfoDefs(),
                    totalSourceHits, totalSourceHits, false);
            for (long i = 0; i < totalSourceHits; i++) {
                if (globalProperty.get(i).equals(value)) {
                    // This hit matches the filter, add it to the results
                    EphemeralHit hit = new EphemeralHit();
                    getEphemeral(i, hit);
                    allFilteredHits.add(hit);
                }
            }
            return allFilteredHits;
        }
    }

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

    default Map<LeafReaderContext, Hits> hitsPerSegment() {
        return null;
    }

    /** For lazy Hits implementations, returns the current size.
     * For non-lazy implementations, just returns size().
     */
    long sizeSoFar();

    /** For grouping */
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

        public Group merge(Group segmentGroup, long maxValuesToStorePerGroup) {
            if (maxValuesToStorePerGroup >= 0 && storedHits.size() + segmentGroup.storedHits.size() > maxValuesToStorePerGroup) {
                // Can we hold any more hits?
                if (storedHits.size() < maxValuesToStorePerGroup) {
                    // We can add a limited number of hits, so we need to trim the segment group
                    Hits hitsToAdd = segmentGroup.storedHits
                            .sublist(0, maxValuesToStorePerGroup - storedHits.size());
                    storedHits.addAll(hitsToAdd);
                }
            } else {
                // Just add all the hits
                storedHits.addAll(segmentGroup.getStoredHits());
            }
            totalNumberOfHits += segmentGroup.totalNumberOfHits;
            return this;
        }
    }
}
