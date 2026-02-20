package nl.inl.blacklab.search.results.hits;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import org.apache.lucene.index.LeafReaderContext;

import com.ibm.icu.text.CollationKey;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropContext;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.results.hitresults.Concordances;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hitresults.Kwics;
import nl.inl.blacklab.search.results.hits.fetch.HitPublisher;
import nl.inl.blacklab.search.results.hits.fetch.HitSubscriber;

public abstract class HitsAbstract implements Hits {

    public static void setGroupAfterFetching(boolean groupAfterFetching) {
        HitsAbstract.groupAfterFetching = groupAfterFetching;
    }

    /** If true, fetch all hits first before grouping. If false, group while fetching hits.
     *  (performance testing) */
    private static boolean groupAfterFetching = true;

    @Override
    public int countDocs(long startIndex, long endIndex) {
        Set<Integer> docs = new HashSet<>();
        for (long hitIndex = startIndex; hitIndex < endIndex; hitIndex++) {
            docs.add(doc(hitIndex));
        }
        return docs.size();
    }

    @Override
    public boolean hasMatchInfo() {
        return matchInfoDefs().currentSize() > 0;
    }

    @Override
    public boolean isEmpty() {
        return !sizeAtLeast(1);
    }

    @Override
    public PermanentHit get(long index) {
        EphemeralHit hit = new EphemeralHit();
        getEphemeral(index, hit);
        return hit.solidify();
    }

    @Override
    public Hits sublist(long start, long length) {
        if (start < 0)
            throw new IndexOutOfBoundsException("Window start must be non-negative, but was " + start);
        if (length < 0)
            throw new IllegalArgumentException("Window size must be non-negative, but was " + length);
        if (length == 0 || !sizeAtLeast(start + 1))
            return Hits.empty(context());
        sizeAtLeast(start + length); // try to ensure we have enough hits
        long end = start + length;
        long actualHitsAvailable = sizeSoFar();
        if (end > actualHitsAvailable)
            end = actualHitsAvailable;
        HitsMutable window = HitsMutable.create(context(),
                end - start, false, false);
        EphemeralHit h = new EphemeralHit();
        for (long i = start; i < end; ++i) {;
            getEphemeral(i, h);
            window.add(h);
        }
        return window;
    }

    @Override
    public Iterator<EphemeralHit> iterator() {
        return new Iterator<>() {
            private long pos = 0;

            private final EphemeralHit hit = new EphemeralHit();

            @Override
            public boolean hasNext() {
                return size() > pos;
            }

            @Override
            public EphemeralHit next() {
                if (!hasNext())
                    throw new NoSuchElementException();
                getEphemeral(pos, hit);
                ++pos;
                return hit;
            }
        };
    }

    @Override
    public Concordances concordances(ContextSize contextSize, ConcordanceType type) {
        if (contextSize == null)
            contextSize = index().defaultContextSize();
        if (type == null)
            type = ConcordanceType.FORWARD_INDEX;
        return new Concordances(getStatic(), type, contextSize);
    }

    @Override
    public Kwics kwics(ContextSize contextSize) {
        if (contextSize == null)
            contextSize = index().defaultContextSize();
        return new Kwics(getStatic(), contextSize);
    }

    @Override
    public Hits filteredByDocId(int docId) {
        HitsMutable hitsInDoc = HitsMutable.create(context(),
                -1, size(), false);
        for (EphemeralHit h: this) {
            if (h.doc() == docId)
                hitsInDoc.add(h);
        }
        return hitsInDoc;
    }

    @Override
    public Hits sorted(HitProperty sortBy) {
        // Fetch all the hits and determine size.
        long n = size();
        HitsListAbstract mergedHits = HitsMutable.create(context(), n, n, false);
        List<Hits> perSegment = hitsPerSegment();
        if (perSegment != null) {
            // Use per-segment hits directly rather than through a global view (which is slower).
            for (Hits segmentHits: perSegment) {
                mergedHits.addAllConvertDocBase(segmentHits);
            }
        } else {
            // Just copy all hits and sort them.
            // (subclass will usually override this method to do it more efficiently)
            mergedHits.addAll(getStatic());
        }
        HitProperty sortByWithContext = sortBy.copyWith(PropContext.globalHits(mergedHits,
                new ConcurrentHashMap<>()));
        // NOTE: We're calling HitsListAbstract.sorted(), not recursing endlessly.
        return mergedHits.sorted(sortByWithContext);
    }

    /** Group these hits by the specified property.
     *
     * @param groupBy property to group by
     * @param maxValuesToStorePerGroup maximum number of hits to store per group
     * @return grouped hits
     */
    @Override
    public Map<PropertyValue, Group> grouped(HitProperty groupBy, long maxValuesToStorePerGroup) {
        Map<PropertyValue, Group> groups = new ConcurrentHashMap<>();
        Map<String, CollationKey> collationCache = new ConcurrentHashMap<>();
        performPerSegment(() -> new SegmentGrouper(collationCache, groupBy, maxValuesToStorePerGroup, groups),
                groupAfterFetching);
        return groups;
    }

    /** Add a batch of hits to a grouping */
    static Map<PropertyValue, Group> groupHits(Hits hits, long startIndex, long endIndex, HitProperty groupBy,
            long maxResultsToStorePerGroup, Map<PropertyValue, Group> groups, LeafReaderContext lrc,
            Map<String, CollationKey> collationCache) {
        // temporary copy used in grouping (don't keep reference to hits)
        // NOTE: we pass toGlobal = true because segment hits must be grouped by global identity (so we can merge them)
        groupBy = groupBy.copyWith(PropContext.segmentToGlobal(hits, lrc, collationCache));

        EphemeralHit hit = new EphemeralHit();
        for (long hitIndex = startIndex; hitIndex < endIndex; hitIndex++) {

            // Determine group indentity
            PropertyValue identity = groupBy.get(hitIndex);

            // Get hit and convert to global if necessary
            hits.getEphemeral(hitIndex, hit);
            if (lrc != null) {
                // This is a segment hit. Convert doc id to global.
                hit.convertDocIdToGlobal(lrc.docBase);
            }

            // Add to correct group
            Group group = groups.get(identity);
            if (group == null) {
                if (groups.size() >= HitGroups.MAX_NUMBER_OF_GROUPS)
                    throw new UnsupportedOperationException(
                            "Cannot handle more than " + HitGroups.MAX_NUMBER_OF_GROUPS + " groups");
                HitsMutable hitsInGroup = HitsMutable.create(
                        hits.context(), -1, hits.size(), false);
                group = new Group(hitsInGroup, 0);
                groups.put(identity, group);
            }
            if (maxResultsToStorePerGroup < 0 || group.storedHits.size() < maxResultsToStorePerGroup) {
                group.storedHits.add(hit);
            }
            group.totalNumberOfHits++;
        }
        return groups;
    }

    @Override
    public void performPerSegment(Supplier<HitSubscriber> subscriberSupplier, boolean prefetchAll) {
        List<HitPublisher> publishers = publishersPerSegment();
        if (publishers == null) {
            // We don't have per-segment hits, so we can't do this in parallel.
            publishers = List.of(publisher());
        }
        final Exception[] thrownException = { null };
        CountDownLatch segmentDoneLatch = new CountDownLatch(publishers.size());
        publishers.forEach(publisher -> {
            if (prefetchAll) {
                // Fetch all hits for this segment first
                publisher = publisher.getStatic().publisher();
            }
            publisher.subscribe(new LatchingHitSubscriber(subscriberSupplier.get(), segmentDoneLatch, thrownException));
        });
        if (thrownException[0] != null)
            throw new RuntimeException(thrownException[0]);

        // Wait for all segments to be done grouping
        try {
            segmentDoneLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /** Wraps a HitSubscriber, saves a thrown exception and counts down when it's done. */
    private static class LatchingHitSubscriber implements HitSubscriber {

        private final CountDownLatch segmentDoneLatch;

        boolean latched;

        HitSubscriber wrapped;

        private final Exception[] thrownException;

        public LatchingHitSubscriber(HitSubscriber wrapped, CountDownLatch segmentDoneLatch, Exception[] thrownException) {
            this.segmentDoneLatch = segmentDoneLatch;
            this.wrapped = wrapped;
            this.thrownException = thrownException;
        }

        @Override
        public void start(LeafReaderContext lrc, Hits results) {
            latched = false;
            wrapped.start(lrc, results);
        }

        @Override
        public boolean needsMoreHits() {
            return wrapped.needsMoreHits();
        }

        @Override
        public void hits(LeafReaderContext lrc, Hits batchHits, long batchStart, long batchEnd,
                int batchNumDocs, long batchOffsetInTotal) {
            wrapped.hits(lrc, batchHits, batchStart, batchEnd, batchNumDocs, batchOffsetInTotal);
        }

        @Override
        public void counted(long hitsCounted, int docsCounted) {
            wrapped.counted(hitsCounted, docsCounted);
        }

        @Override
        public void flush(LeafReaderContext lrc, Hits results) {
            wrapped.flush(lrc, results);
        }

        @Override
        public void done(LeafReaderContext lrc) {
            wrapped.done(lrc);

            // Signal we're done with this segment, so we can wait for all segments to be done below.
            signalDone();
        }

        private synchronized void signalDone() {
            if (!latched) {
                segmentDoneLatch.countDown();
                latched = true;
            }
        }

        @Override
        public void error(LeafReaderContext lrc, Exception exception) {
            wrapped.error(lrc, exception);
            thrownException[0] = exception;
            signalDone();
        }
    }

    /** Performs a grouping operation on a segment.
     *
     * If per-segment publishers aren't available, this can also be used
     * to perform grouping on a global Hits object (just call hits.publisher()).
     */
    private static class SegmentGrouper implements HitSubscriber {

        final Map<PropertyValue, Group> segmentGroups;
        private final Map<String, CollationKey> collationCache;
        private final HitProperty groupBy;
        private final long maxValuesToStorePerGroup;
        private final Map<PropertyValue, Group> groups;

        public SegmentGrouper(Map<String, CollationKey> collationCache, HitProperty groupBy,
                long maxValuesToStorePerGroup,
                Map<PropertyValue, Group> groups) {
            this.collationCache = collationCache;
            this.groupBy = groupBy;
            this.maxValuesToStorePerGroup = maxValuesToStorePerGroup;
            this.groups = groups;
            segmentGroups = new HashMap<>();
        }

        @Override
        public void start(LeafReaderContext lrc, Hits results) {
        }

        @Override
        public boolean needsMoreHits() {
            return true; // we need all hits
        }

        @Override
        public void hits(LeafReaderContext lrc, Hits batchHits, long batchStart, long batchEnd,
                int batchNumDocs,
                long batchOffsetInTotal) {
            Hits toGroup = batchHits.sublist(batchStart, batchEnd - batchStart);
            groupHits(toGroup, 0, toGroup.size(), groupBy,
                    maxValuesToStorePerGroup, segmentGroups, lrc, collationCache);
        }

        @Override
        public void counted(long hitsCounted, int docsCounted) {
            // ignore
        }

        @Override
        public void flush(LeafReaderContext lrc, Hits results) {
            // nothing to do
        }

        @Override
        public void done(LeafReaderContext lrc) {
            // Merge to global groups
            for (Map.Entry<PropertyValue, Group> entry: segmentGroups.entrySet()) {
                PropertyValue groupId = entry.getKey();
                Group segmentGroup = entry.getValue();
                groups.compute(groupId, (PropertyValue k, Group v) ->
                        v == null ? segmentGroup : v.merge(segmentGroup, maxValuesToStorePerGroup));
            }
        }

        @Override
        public void error(LeafReaderContext lrc, Exception exception) {
        }
    }

}
