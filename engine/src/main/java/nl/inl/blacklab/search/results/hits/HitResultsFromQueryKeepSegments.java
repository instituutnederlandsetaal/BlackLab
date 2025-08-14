package nl.inl.blacklab.search.results.hits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.util.PriorityQueue;

import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;

/**
 * A version of HitsFromQuery that keeps the hits from each segment separately.
 * <p>
 * This is useful in case we want to sort or group, which is more efficiently done per-segment, then merged.
 * We also support a global view of the unsorted hits (even while they're being fetched), for operations
 * that don't require all hits, such as returning a page of hits.
 */
public class HitResultsFromQueryKeepSegments extends HitResultsFromQuery {

    /**
     * The step with which hitToStretchMapping records mappings.
     * <p>
     * hitToStretchMapping only has a value for every nth hit index in the
     * global view. So hitToStretchMapping.getInt(i) will return the stretch
     * for hit index i * HIT_INDEX_TO_STRETCH_STEP.
     */
    private static final long HIT_INDEX_TO_STRETCH_STEP = 100;

    /**
     * Desired minimum length of a stretch of segment hits in the global view.
     * <p>
     * Note that we DO sometimes add stretches smaller than this if we happen to be done
     * collecting hits for now and need to add the last hits to the global view.
     */
    private static final int STRETCH_THRESHOLD_MINIMUM = 10;

    /**
     * Maximum for the stretch threshold, i.e. the point at which we decide
     * we have enough hits to add a stretch to the global view.
     * <p>
     * We don't want to add stretches that are too large, because that would
     * make it take too long for the hits to become available in the global view.
     */
    private static final int STRETCH_THRESHOLD_MAXIMUM = 1000;

    /** Divider for the size of new stretches.
     * <p>
     * For efficiency, we make new stretches a fraction of the total number of hits so far,
     * so the size of new stretches grows as the number of hits retrieved grows.
     */
    public static final int STRETCH_SIZE_DIVIDER = 10;

    /** A stretch of hits from a segment.
     * <p>
     * We use these to construct the global view.
     */
    static class HitsStretch {
        /** Stretch index, for finding next stretch quickly. */
        int stretchIndex;

        /** This segment's docBase, for converting from segment to global doc ids. */
        int docBase;

        /** Segment this stretch is from. */
        HitsInternalMutable segmentHits;

        /** Start index in the segment hits. */
        long firstHitSegment;

        /** Start index in the global hits view. */
        long firstHitGlobal;

        /** Length of this stretch. */
        long stretchLength;

        public HitsStretch(int stretchIndex, int docBase, HitsInternalMutable segmentHits, long firstHitSegment,
                long firstHitGlobal, long stretchLength) {
            this.stretchIndex = stretchIndex;
            this.docBase = docBase;
            this.segmentHits = segmentHits;
            this.firstHitSegment = firstHitSegment;
            this.firstHitGlobal = firstHitGlobal;
            this.stretchLength = stretchLength;
        }

        /** Get the associated segment's hits */
        HitsInternalMutable segmentHits() {
            return segmentHits;
        }

        /** Does this stretch contain this global hit index? */
        public boolean containsGlobalIndex(long index) {
            // Check if this stretch contains the given global hit index.
            // The global hit index is the index in the global hits view, which is a concatenation of all segment hits.
            return index >= firstHitGlobal && index < firstHitGlobal + stretchLength;
        }

        /** Convert global hit index to segment hit index */
        public long globalToSegmentIndex(long globalIndex) {
            // Calculate the index of this global hit in the segment's hits.
            assert containsGlobalIndex(globalIndex);
            long indexInSegment = globalIndex - firstHitGlobal + firstHitSegment;
            assert indexInSegment >= 0 : "Index in segment out of bounds: " + indexInSegment;
            return indexInSegment;
        }

        @Override
        public String toString() {
            return "HitsStretch{" +
                    "docBase=" + segmentHits +
                    ", stretchIndex=" + stretchIndex +
                    ", segHits=" + segmentHits +
                    ", segStart=" + firstHitSegment +
                    ", globalStart=" + firstHitGlobal +
                    ", length=" + stretchLength +
                    "}";
        }
    }

    /** The hits per segment. */
    private Map<LeafReaderContext, HitsInternalMutable> hitsPerSegment;

    /** Number of hits in the global view. */
    protected long numHitsGlobalView = 0;

    /** The stretches that make up our global hits view, in order */
    private final ObjectList<HitsStretch> stretches = new ObjectArrayList<>();

    /** Records the stretch index for every nth hit (n = {@link #HIT_INDEX_TO_STRETCH_STEP}).
     * The stretch for another hit index can then be found using linear search from
     * this stretch index if needed.
     */
    private final IntBigList hitToStretchMapping = new IntBigArrayBigList();

    protected HitResultsFromQueryKeepSegments(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        super(queryInfo, sourceQuery, searchSettings);
        if (hitsPerSegment == null) {
            // (can only happen if there are no spans - normally initialized in getSpansReaderStrategy())
            hitsPerSegment = new LinkedHashMap<>();
        }
        // Global view on our segment hits
        hitsView = new LazyGlobalHitsView();
    }

    /** Number of hits in the global view. Needed because we don't want to call hitsInternalMutable.size() from
     *  HitsFromQueryKeepSegments, because that class doesn't use it. (REFACTOR THIS!) */
    protected long globalHitsSoFar() {
        return numHitsGlobalView;
    }

    @Override
    public Map<LeafReaderContext, HitsSimple> getSegmentHits() {
        return Collections.unmodifiableMap(hitsPerSegment);
    }

    protected SpansReader.Strategy getSpansReaderStrategy(LeafReaderContext lrc) {
        // We'll collect the segment hits here. It has to lock, because we'll be writing and reading from it.
        final HitsInternalMutable hitsInThisSegment = HitsInternalMutable.create(field(), hitQueryContext.getMatchInfoDefs(),
                -1, true, true);

        // Add it to the map of segment hits.
        if (hitsPerSegment == null)
            hitsPerSegment = new LinkedHashMap<>();
        hitsPerSegment.put(lrc, hitsInThisSegment); // only called from constructor, so no need to synchronize

        // Return a strategy that will add hits to the segment hits and maintain the global view.
        return new GlobalViewIntoSegmentHitsStrategy(hitsInThisSegment, lrc);
    }

    /** A stable global view of the lists of segment hits */
    private class LazyGlobalHitsView implements HitsSimple {

        /** Below this number of hits, we'll sort in a single thread to save overhead. */
        public static final int THRESHOLD_SINGLE_THREADED_SORT = 100;

        @Override
        public AnnotatedField field() {
            return HitResultsFromQueryKeepSegments.this.field();
        }

        @Override
        public BlackLabIndex index() {
            return HitResultsFromQueryKeepSegments.this.index();
        }

        @Override
        public MatchInfoDefs matchInfoDefs() {
            return hitQueryContext.getMatchInfoDefs();
        }

        @Override
        public long size() {
            ensureResultsRead(-1);
            synchronized (HitResultsFromQueryKeepSegments.this) {
                return numHitsGlobalView;
            }
        }

        @Override
        public boolean isEmpty() {
            return !ensureResultsRead(1);
        }

        @Override
        public Hit get(long index) {
            EphemeralHit hit = new EphemeralHit();
            getEphemeral(index, hit);
            return hit.toHit();
        }

        /** Get the stretch a certain hit is part of */
        private HitsStretch getHitsStretch(long index) {
            synchronized (HitResultsFromQueryKeepSegments.this) {
                if (index < 0 || index >= numHitsGlobalView)
                    throw new IndexOutOfBoundsException(
                            "Hit index " + index + " is out of bounds (size: " + numHitsGlobalView + ")");

                // Round down to nearest stretch index and get the stretch for that index.
                long indexInMapping = index / HIT_INDEX_TO_STRETCH_STEP;
                int stretchIndex = hitToStretchMapping.getInt(indexInMapping);
                HitsStretch stretch = stretches.get(stretchIndex);

                // If the stretch doesn't contain the global index, find the next stretch that does.
                while (!stretch.containsGlobalIndex(index)) {
                    stretchIndex++;
                    stretch = stretches.get(stretchIndex);
                }
                return stretch;
            }
        }

        @Override
        public void getEphemeral(long index, EphemeralHit hit) {
            ensureResultsRead(index + 1);
            HitsStretch stretch = getHitsStretch(index);
            stretch.segmentHits().getEphemeral(stretch.globalToSegmentIndex(index), hit);
            convertToGlobal(hit, stretch.docBase);
        }

        @Override
        public int doc(long index) {
            ensureResultsRead(index + 1);
            HitsStretch stretch = getHitsStretch(index);
            return stretch.segmentHits().doc(stretch.globalToSegmentIndex(index)) + stretch.docBase;
        }

        @Override
        public int start(long index) {
            ensureResultsRead(index + 1);
            HitsStretch stretch = getHitsStretch(index);
            return stretch.segmentHits().start(stretch.globalToSegmentIndex(index));
        }

        @Override
        public int end(long index) {
            ensureResultsRead(index + 1);
            HitsStretch stretch = getHitsStretch(index);
            return stretch.segmentHits().end(stretch.globalToSegmentIndex(index));
        }

        @Override
        public MatchInfo[] matchInfos(long hitIndex) {
            ensureResultsRead(hitIndex + 1);
            HitsStretch stretch = getHitsStretch(hitIndex);
            return stretch.segmentHits().matchInfos(stretch.globalToSegmentIndex(hitIndex));
        }

        @Override
        public MatchInfo matchInfo(long hitIndex, int matchInfoIndex) {
            ensureResultsRead(hitIndex + 1);
            HitsStretch stretch = getHitsStretch(hitIndex);
            return stretch.segmentHits().matchInfo(stretch.globalToSegmentIndex(hitIndex), matchInfoIndex);
        }

        @Override
        public HitsSimple getStatic() {
            return this;
        }

        @Override
        public HitsSimple sublist(long start, long length) {
            if (length == 0)
                return HitsSimple.empty(field(), matchInfoDefs());
            ensureResultsRead(start + length);
            long end = start + length;
            synchronized (HitResultsFromQueryKeepSegments.this) {
                if (end > numHitsGlobalView)
                    end = numHitsGlobalView;
            }
            if (start == end)
                return HitsSimple.empty(field(), matchInfoDefs());
            if (start < 0 || end < 0 || start > end)
                throw new IndexOutOfBoundsException("Sub-list start " + start + " with length " + length +
                        " is out of bounds (size: " + size() + ")");

            HitsInternalMutable sublist = HitsInternalMutable.create(field(), matchInfoDefs(), end - start, false, false);
            EphemeralHit h = new EphemeralHit();
            long globalIndex = start;
            HitsStretch currentStretch = getHitsStretch(globalIndex);
            long indexInSegment = currentStretch.globalToSegmentIndex(globalIndex);
            if (indexInSegment < 0)
                throw new IllegalStateException("Negative index in segment: " + indexInSegment +
                        " (global index: " + globalIndex + ", stretch: " + currentStretch + ")");
            long hitsLeftInStretch = currentStretch.stretchLength - (start - currentStretch.firstHitGlobal);
            while (true) {
                // Add a hit from the current stretch to the sublist.
                currentStretch.segmentHits().getEphemeral(indexInSegment, h);
                convertToGlobal(h, currentStretch.docBase);
                sublist.add(h);
                indexInSegment++;

                // Are we done?
                globalIndex++;
                if (globalIndex == end)
                    break;

                // If we reached the end of the current stretch, go to the next stretch.
                hitsLeftInStretch--;
                if (hitsLeftInStretch == 0) {
                    currentStretch = stretches.get(currentStretch.stretchIndex + 1);
                    indexInSegment = currentStretch.globalToSegmentIndex(globalIndex);
                    hitsLeftInStretch = currentStretch.stretchLength - (start - currentStretch.firstHitGlobal);
                }
            }
            return sublist;
        }

        @Override
        public Iterator<EphemeralHit> iterator() {
            return new Iterator<>() {

                /** Iterate over all stretches so we can iterate over each hit within them */
                Iterator<HitsStretch> stretchIterator = stretches.iterator();

                /** The current stretch of hits we're iterating over */
                HitsStretch currentStretch = null;

                /** Global hit index of the last hit produced */
                long globalIndex = -1;

                /** We populate this and return it in next() */
                final EphemeralHit hit = new EphemeralHit();

                @Override
                public boolean hasNext() {
                    long nextHitIndex = globalIndex + 1;
                    return ensureResultsRead(nextHitIndex + 1);
                }

                @Override
                public EphemeralHit next() {
                    if (!hasNext())
                        throw new IndexOutOfBoundsException("No more hits available (index: " + globalIndex + ")");
                    globalIndex++;

                    // Make sure we have the right stretch for this hit index.
                    while (currentStretch == null || !currentStretch.containsGlobalIndex(globalIndex)) {
                        // We need to find the stretch for this hit index.
                        currentStretch = stretchIterator.next();
                    }
                    currentStretch.segmentHits().getEphemeral(currentStretch.globalToSegmentIndex(globalIndex), hit);
                    convertToGlobal(hit, currentStretch.docBase);
                    return hit;
                }
            };
        }

        /** The state of a segment during a merge */
        static class SegmentInMerge {

            /** Hits from this segment */
            HitsSimple hits;

            /** Segment's docBase, for converting segment doc id to global doc id */
            int docBase;

            /** Property to sort on */
            HitProperty sortProp;

            /** Index of the next hit from this segment to merge */
            int index;

            public SegmentInMerge(HitsSimple segmentHits, int docBase, HitProperty globalSortProp) {
                this.hits = segmentHits;
                this.docBase = docBase;
                this.sortProp = globalSortProp.copyWith(segmentHits);
                sortProp.setDocBase(docBase);
                this.index = 0;
            }

            boolean done() {
                return index >= hits.size();
            }

            PropertyValue sortValue() {
                return sortProp.get(index);
            }

            void getHitAndAdvance(EphemeralHit hit) {
                hits.getEphemeral(index, hit);
                index++;
            }
        }

        /** Priority queue for merging n already-sorted lists of hits. */
        static class MergePriorityQueue extends PriorityQueue<SegmentInMerge> {

            public MergePriorityQueue(int maxSize) {
                super(maxSize);
            }

            @Override
            protected final boolean lessThan(SegmentInMerge a, SegmentInMerge b) {
                if (a.done()) {
                    // Either a is done, or both are done.
                    // In either case, a is not less than b.
                    return false;
                }
                if (b.done()) {
                    // b is done but a is not, so a is less than b
                    // (that is, there's still hits to merge in a, so bubble it up to the top of the heap).
                    return true;
                }
                // Both still have hits to merge.
                // Compare the sort values of the hits from each segment.
                return a.sortValue().compareTo(b.sortValue()) < 0;
            }
        }

        /** Hits from a single segment */
        record SegmentHits(
                LeafReaderContext lrc,
                HitsSimple hits) {
            public Long numberOfHits() {
                return hits.size();
            }
        }

        @Override
        public HitsSimple sorted(HitProperty sortProp) {
            // Fetch all the hits and determine size.
            size();
            if (numHitsGlobalView < THRESHOLD_SINGLE_THREADED_SORT) {
                // If there are only a few hits, just sort them in a single thread.
                return sortedSingleThread(sortProp);
            }

            // We need to sort the hits from each segment separately (in parallel), then merge them.

            // Group them together so that each group has approximately the same number of hits.
            List<List<SegmentHits>> groups =
                    sortMakeEqualGroups(hitsPerSegment, numThreads);

            // TODO: make sort and merge steps abortable (ThreadAborter)

            // Sort each group of segments in a separate thread.
            List<Future<List<SegmentHits>>> pendingResults = sortGroups(groups, sortProp, getExecutorService());

            // Add the results to the priority queue for merging.
            MergePriorityQueue queue = sortGatherResults(hitsPerSegment.size(), sortProp, pendingResults);

            // Now merge the sorted hits from each segment.
            HitsInternalMutable mergedHits = HitsInternalMutable.create(field(), matchInfoDefs(),
                    numHitsGlobalView, numHitsGlobalView, false);
            return sortMerge(queue, mergedHits);
        }

        /** Separate the hits from each segment into numThreads equals groups */
        private static List<List<SegmentHits>> sortMakeEqualGroups(
                Map<LeafReaderContext, HitsInternalMutable> hitsPerSegment, int numThreads) {

            // First sort segment hits by decreasing size.
            List<SegmentHits> segmentsHits = new ArrayList<>();
            for (Map.Entry<LeafReaderContext, HitsInternalMutable> entry : hitsPerSegment.entrySet()) {
                segmentsHits.add(new SegmentHits(entry.getKey(), entry.getValue()));
            }
            return makeEqualGroups(segmentsHits, SegmentHits::numberOfHits, numThreads);
        }

        /** Sort each group of segment hits in a separate thread */
        private static List<Future<List<SegmentHits>>> sortGroups(
                List<List<SegmentHits>> groups, HitProperty sortProp,
                ExecutorService executorService) {
            List<Future<List<SegmentHits>>> pendingResults = new ArrayList<>();
            for (List<SegmentHits> group: groups) {
                Future<List<SegmentHits>> future = executorService.submit(() ->
                        group.stream().map(segment -> {
                            // For each segment, sort the hits using the specified property.
                            HitProperty sortPropSegment =
                                    sortProp.copyWith(segment.hits, segment.lrc, false);
                            return new SegmentHits(segment.lrc, segment.hits.sorted(sortPropSegment));
                        }).toList());
                pendingResults.add(future);
            }
            return pendingResults;
        }

        /** Gather sorted segment hits and put them in the merge queue */
        private static MergePriorityQueue sortGatherResults(int numberOfSegments, HitProperty sortProp,
                List<Future<List<SegmentHits>>> pendingResults) {
            MergePriorityQueue queue = new MergePriorityQueue(numberOfSegments);
            for (Future<List<SegmentHits>> future: pendingResults) {
                try {
                    for (SegmentHits segmentResults: future.get()) {
                        queue.add(new SegmentInMerge(segmentResults.hits, segmentResults.lrc.docBase, sortProp));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // preserve interrupted status
                    throw new InterruptedSearch(e);
                } catch (ExecutionException e) {
                    throw new InterruptedSearch(e);
                }
            }
            return queue;
        }

        /** Merge the sorted segment hits */
        private static HitsInternalMutable sortMerge(MergePriorityQueue queue, HitsInternalMutable mergedHits) {
            while (!queue.top().done()) {
                SegmentInMerge segment = queue.top();
                EphemeralHit hit = new EphemeralHit();
                segment.getHitAndAdvance(hit);
                convertToGlobal(hit, segment.docBase);
                mergedHits.add(hit);
                queue.updateTop(); // re-order the queue after taking a hit
            }
            return mergedHits;
        }

        /** Sort the hits in a single thread. */
        private HitsSimple sortedSingleThread(HitProperty sortProp) {
            size(); // Fetch all hits. Was already called, but just in case we reuse this method.
            HitsInternalMutable mergedHits = HitsInternalMutable.create(field(), matchInfoDefs(),
                    numHitsGlobalView, numHitsGlobalView, false);
            for (Map.Entry<LeafReaderContext, HitsInternalMutable> segmentHits: hitsPerSegment.entrySet()) {
                for (EphemeralHit hit: segmentHits.getValue()) {
                    convertToGlobal(hit, segmentHits.getKey().docBase);
                    mergedHits.add(hit);
                }
            }
            return mergedHits.sorted(sortProp);
        }

        @Override
        public boolean hasMatchInfo() {
            return hitQueryContext.numberOfMatchInfos() > 0;
        }
    }

    /** Deal with new hits by adding stretches to the global view. */
    private class GlobalViewIntoSegmentHitsStrategy implements SpansReader.Strategy {

        /** The segment we're looking at */
        private final LeafReaderContext lrc;

        /** The list of hits in this segment to add new hits to */
        private final HitsInternalMutable segmentHits;

        /** First hit from segmentHits that's not yet part of the global view. */
        long indexInSegmentHits;

        public GlobalViewIntoSegmentHitsStrategy(HitsInternalMutable segmentHits, LeafReaderContext lrc) {
            this.segmentHits = segmentHits;
            this.lrc = lrc;
            indexInSegmentHits = 0;
        }

        @Override
        public void onDocumentBoundary(HitsInternalMutable results) {
            // Add new hits to the segment results.
            segmentHits.addAll(results);

            // Add them to the global view? We only do this on a boundary
            // between documents, so hits from the same doc remain
            // contiguous in the global view.

            // Note that we add small stretches at first, so the first page of hits is
            // available quickly. Later, we add them in larger batches to reduce overhead.
            // (note that we don't synchronize for numberOfHits because we don't care if we get a slightly
            //  out of date (i.e. too small) value here)
            long addHitsToGlobalThreshold = clamp(numHitsGlobalView / STRETCH_SIZE_DIVIDER, STRETCH_THRESHOLD_MINIMUM,
                    STRETCH_THRESHOLD_MAXIMUM);
            addStretchIfLargeEnough(addHitsToGlobalThreshold);
            results.clear();
        }

        // (java 17 doesn't have Math.clamp, so we implement it ourselves)
        private long clamp(long number, long min, long max) {
            return Math.max(min, Math.min(max, number));
        }

        @Override
        public void onFinished(HitsInternalMutable results) {
            // Add the final batch of hits to the segment results.
            segmentHits.addAll(results);
            addStretchIfLargeEnough(0);
        }

        /** Add the latest stretch of hits we've found to the global view. */
        private void addStretchIfLargeEnough(long threshold) {
            if (segmentHits.size() - indexInSegmentHits <= threshold)
                return; // not enough hits to add a stretch
            // Create a new stretch for the global hits view.
            // Start where the last stretch in this segment ended.
            long length = segmentHits.size() - indexInSegmentHits;
            assert length > 0;
            synchronized (HitResultsFromQueryKeepSegments.this) {
                HitsStretch stretch = new HitsStretch(stretches.size(), lrc.docBase,
                        segmentHits, indexInSegmentHits, numHitsGlobalView, length);
                stretches.add(stretch);
                indexInSegmentHits += length;
                numHitsGlobalView += length;

                // Add hitToStretchMappings for the appropriate indexes, so we can quickly find the stretch
                // for a global hit index. (we record a mapping every HIT_INDEX_TO_STRETCH_STEP)
                long hitsSinceLastMapping = stretch.firstHitGlobal % HIT_INDEX_TO_STRETCH_STEP;
                if (hitsSinceLastMapping == 0)
                    hitsSinceLastMapping = HIT_INDEX_TO_STRETCH_STEP;
                long nextMappingIndex = stretch.firstHitGlobal + (HIT_INDEX_TO_STRETCH_STEP - hitsSinceLastMapping);
                while (nextMappingIndex < stretch.firstHitGlobal + length) {
                    // Add an entry for this global hit index, so we can quickly find the stretch it belongs to.
                    hitToStretchMapping.add(stretches.size() - 1);
                    nextMappingIndex += HIT_INDEX_TO_STRETCH_STEP;
                }
            }
        }
    }
}
