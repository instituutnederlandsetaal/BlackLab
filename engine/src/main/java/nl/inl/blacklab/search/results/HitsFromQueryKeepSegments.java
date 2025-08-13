package nl.inl.blacklab.search.results;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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

/**
 * A version of HitsFromQuery that keeps the hits from each segment separately.
 * <p>
 * This is useful in case we want to sort or group, which is more efficiently done per-segment, then merged.
 * We also support a global view of the unsorted hits (even while they're being fetched), for operations
 * that don't require all hits, such as returning a page of hits.
 */
public class HitsFromQueryKeepSegments extends HitsFromQuery {

    /** hitToStretchMapping has a value for every nth hit index in the global view.
     * <p>
     * So hitToStretchMapping.getInt(i) will return the stretch for hit index i * HIT_INDEX_TO_STRETCH_STEP.
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

        /** Segment this stretch is from. */
        HitsInternalMutable segHits;

        /** Start index in the segment hits. */
        long segStart;

        /** Start index in the global hits view. */
        long globalStart;

        /** Length of this stretch. */
        long length;

        public HitsStretch(int stretchIndex, HitsInternalMutable segHits, long segStart, long globalStart, long length) {
            this.stretchIndex = stretchIndex;
            this.segHits = segHits;
            this.segStart = segStart;
            this.globalStart = globalStart;
            this.length = length;
        }


        /** Get the associated segment's hits */
        HitsInternalMutable segmentHits() {
            return segHits;
        }

        public boolean containsGlobalIndex(long index) {
            // Check if this stretch contains the given global hit index.
            // The global hit index is the index in the global hits view, which is a concatenation of all segment hits.
            return index >= globalStart && index < globalStart + length;
        }

        public long globalToSegmentIndex(long globalIndex) {
            // Calculate the index of this global hit in the segment's hits.
            assert containsGlobalIndex(globalIndex);
            long indexInSegment = globalIndex - globalStart + segStart;
            assert indexInSegment >= 0 : "Index in segment out of bounds: " + indexInSegment;
            return indexInSegment;
        }

        @Override
        public String toString() {
            return "HitsStretch{" +
                    "stretchIndex=" + stretchIndex +
                    ", segHits=" + segHits +
                    ", segStart=" + segStart +
                    ", globalStart=" + globalStart +
                    ", length=" + length +
                    "}";
        }
    }

    /** The hits per segment. */
    private Map<LeafReaderContext, HitsInternalMutable> hitsPerSegment;

    /** Number of hits in the global view. */
    protected long numberOfHits = 0;

    /** The stretches that make up our global hits view, in order */
    private final ObjectList<HitsStretch> stretches = new ObjectArrayList<>();

    /** Records the stretch index for every nth hit (n = {@link #HIT_INDEX_TO_STRETCH_STEP}) */
    private final IntBigList hitToStretchMapping = new IntBigArrayBigList();

    protected HitsFromQueryKeepSegments(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        super(queryInfo, sourceQuery, searchSettings);
        if (hitsPerSegment == null) {
            // (can only happen if there are no spans - normally initialized in getSpansReaderStrategy())
            hitsPerSegment = new LinkedHashMap<>();
        }
        // Global view on our segment hits
        hitsSimple = new HitsSimple() {

            /** Below this number of hits, we'll sort in a single thread to save overhead. */
            public static final int THRESHOLD_SINGLE_THREADED_SORT = 100;

            @Override
            public AnnotatedField field() {
                return HitsFromQueryKeepSegments.this.field();
            }

            @Override
            public BlackLabIndex index() {
                return HitsFromQueryKeepSegments.this.index();
            }

            @Override
            public MatchInfoDefs matchInfoDefs() {
                return hitQueryContext.getMatchInfoDefs();
            }

            @Override
            public long size() {
                ensureResultsRead(-1);
                synchronized (HitsFromQueryKeepSegments.this) {
                    return numberOfHits;
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
                synchronized (HitsFromQueryKeepSegments.this) {
                    if (index < 0 || index >= numberOfHits)
                        throw new IndexOutOfBoundsException(
                                "Hit index " + index + " is out of bounds (size: " + numberOfHits + ")");

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
            }

            @Override
            public int doc(long index) {
                ensureResultsRead(index + 1);
                HitsStretch stretch = getHitsStretch(index);
                return stretch.segmentHits().doc(stretch.globalToSegmentIndex(index));
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
                    return HitsInternal.empty(field(), matchInfoDefs());
                ensureResultsRead(start + length);
                long end = start + length;
                synchronized (HitsFromQueryKeepSegments.this) {
                    if (end > numberOfHits)
                        end = numberOfHits;
                }
                if (start == end)
                    return HitsInternal.empty(field(), matchInfoDefs());
                if (start < 0 || end < 0 || start > end)
                    throw new IndexOutOfBoundsException("Sub-list start " + start + " with length " + length +
                            " is out of bounds (size: " + size() + ")");

                HitsInternalMutable sublist = HitsInternal.create(field(), matchInfoDefs(), end - start, false, false);
                EphemeralHit h = new EphemeralHit();
                long globalIndex = start;
                HitsStretch currentStretch = getHitsStretch(globalIndex);
                long indexInSegment = currentStretch.globalToSegmentIndex(globalIndex);
                if (indexInSegment < 0)
                    throw new IllegalStateException("Negative index in segment: " + indexInSegment +
                            " (global index: " + globalIndex + ", stretch: " + currentStretch + ")");
                long hitsLeftInStretch = currentStretch.length - (start - currentStretch.globalStart);
                while (true) {
                    currentStretch.segmentHits().getEphemeral(indexInSegment, h);
                    sublist.add(h);
                    indexInSegment++;
                    globalIndex++;
                    if (globalIndex == end)
                        break; // we're done
                    hitsLeftInStretch--;
                    if (hitsLeftInStretch == 0) {
                        // Go to the next stretch.
                        currentStretch = stretches.get(currentStretch.stretchIndex + 1);
                        indexInSegment = currentStretch.globalToSegmentIndex(globalIndex);
                        hitsLeftInStretch = currentStretch.length - (start - currentStretch.globalStart);
                    }
                }
                return sublist;
            }

            @Override
            public Iterator<EphemeralHit> ephemeralIterator() {
                return new Iterator<>() {

                    Iterator<HitsStretch> stretchIterator = stretches.iterator();

                    HitsStretch currentStretch = null; // current stretch of hits we're iterating over

                    long index = -1;

                    final EphemeralHit hit = new EphemeralHit();

                    @Override
                    public boolean hasNext() {
                        long nextHitIndex = index + 1;
                        return ensureResultsRead(nextHitIndex + 1);
                    }

                    @Override
                    public EphemeralHit next() {
                        if (!hasNext())
                            throw new IndexOutOfBoundsException("No more hits available (index: " + index + ")");
                        index++;

                        // Make sure we have the right stretch for this hit index.
                        while (currentStretch == null || !currentStretch.containsGlobalIndex(index)) {
                            // We need to find the stretch for this hit index.
                            currentStretch = stretchIterator.next();
                        }
                        currentStretch.segmentHits().getEphemeral(currentStretch.globalToSegmentIndex(index), hit);
                        return hit;
                    }
                };
            }

            /** The state of a segment during a merge */
            static class SegmentInMerge {

                /** Hits from this segment */
                HitsSimple hits;

                /** Index of the next hit from this segment to merge */
                int index;

                /** Property to sort on */
                HitProperty sortProp;

                public SegmentInMerge(HitsSimple hits, HitProperty sortProp) {
                    this.hits = hits;
                    this.sortProp = sortProp.copyWith(hits);
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

            @Override
            public HitsSimple sorted(HitProperty sortProp) {
                // Create target hits object for the merged results.
                // (this triggers fetching all the hits because we call size())
                long numberOfHits = size();
                HitsInternalMutable mergedHits = HitsInternal.create(field(), matchInfoDefs(), numberOfHits, numberOfHits, false);

                if (numberOfHits < THRESHOLD_SINGLE_THREADED_SORT) {
                    // If there are only a few hits; just sort them in a single thread.
                    for (HitsInternalMutable segmentHits: hitsPerSegment.values()) {
                        mergedHits.addAll(segmentHits);
                    }
                    return mergedHits.sorted(sortProp);
                }

                // We need to sort the hits from each segment separately (in parallel), then merge them.
                ExecutorService executorService = getExecutorService();
                final AtomicLong threadNumber = new AtomicLong();
                List<Future<List<HitsSimple>>> pendingResults = hitsPerSegment.entrySet().stream()
                        // subdivide the list, one sublist per thread to use (one list in case of single thread).
                        .collect(Collectors.groupingBy(e -> threadNumber.getAndIncrement() % numThreads))
                        .values()
                        .stream()
                        .map(list -> executorService.submit(() -> list.stream().map(entry -> {
                            // For each segment, sort the hits using the specified property.
                            HitsInternalMutable hits = entry.getValue();
                            HitProperty sortPropSegment = sortProp.copyWith(hits, entry.getKey(), false);
                            return hits.sorted(sortPropSegment);
                        }).toList())) // now submit one task per sublist
                        .toList(); // gather the futures

                // Wait for all segments to be sorted. Add the results to the priority queue for merging.
                MergePriorityQueue queue = new MergePriorityQueue(hitsPerSegment.size());
                for (Future<List<HitsSimple>> future: pendingResults) {
                    try {
                        for (HitsSimple hits: future.get()) {
                            queue.add(new SegmentInMerge(hits, sortProp));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // preserve interrupted status
                        throw new InterruptedSearch(e);
                    } catch (ExecutionException e) {
                        throw new InterruptedSearch(e);
                    }
                }

                // Now merge the sorted hits from each segment.
                while (!queue.top().done()) {
                    SegmentInMerge segment = queue.top();
                    EphemeralHit hit = new EphemeralHit();
                    segment.getHitAndAdvance(hit);
                    queue.updateTop(); // re-order the queue after taking a hit
                    mergedHits.add(hit);
                }
                return mergedHits;
            }

            @Override
            public boolean hasMatchInfo() {
                return hitQueryContext.numberOfMatchInfos() > 0;
            }
        };
    }

    /** Number of hits in the global view. Needed because we don't want to call hitsInternalMutable.size() from
     *  HitsFromQueryKeepSegments, because that class doesn't use it. (REFACTOR THIS!) */
    protected long globalHitsSoFar() {
        return numberOfHits;
    }

    @Override
    public Map<LeafReaderContext, HitsSimple> getSegmentHits() {
        return Collections.unmodifiableMap(hitsPerSegment);
    }

    protected SpansReader.Strategy getSpansReaderStrategy(LeafReaderContext lrc) {
        // We'll collect the segment hits here. It has to lock, because we'll be writing and reading from
        // it at the same time.
        final HitsInternalMutable hitsInThisSegment = HitsInternal.create(field(), hitQueryContext.getMatchInfoDefs(),
                -1, true, true);
        if (hitsPerSegment == null)
            hitsPerSegment = new LinkedHashMap<>();
        hitsPerSegment.put(lrc, hitsInThisSegment); // only called from constructor, so no need to synchronize
        return new SpansReader.Strategy() {

            long lastStretchEnd = 0; // last stretch end index in the global hits view

            @Override
            public void onDocumentBoundary(HitsInternalMutable results) {
                // Add new hits to the segment results.
                hitsInThisSegment.addAll(results);

                // Add them to the global view? We only do this on a boundary
                // between documents, so hits from the same doc remain
                // contiguous in the global view.

                // Note that we add small stretches at first, so the first page of hits is
                // available quickly. Later, we add them in larger batches to reduce overhead.
                // (note that we don't synchronize for numberOfHits because we don't care if we get a slightly
                //  out of date (i.e. too small) value here)
                long addHitsToGlobalThreshold = clamp(numberOfHits / STRETCH_SIZE_DIVIDER, STRETCH_THRESHOLD_MINIMUM,
                        STRETCH_THRESHOLD_MAXIMUM);
                addStretch(addHitsToGlobalThreshold);
                results.clear();
            }

            // (java 17 doesn't have Math.clamp, so we implement it ourselves)
            private long clamp(long number, long min, long max) {
                return Math.max(min, Math.min(max, number));
            }

            @Override
            public void onFinished(HitsInternalMutable results) {
                // Add the final batch of hits to the segment results.
                hitsInThisSegment.addAll(results);
                addStretch(0);
            }

            /** Add the latest stretch of hits we've found to the global view. */
            private void addStretch(long threshold) {
                if (hitsInThisSegment.size() - lastStretchEnd <= threshold)
                    return; // not enough hits to add a stretch
                // Create a new stretch for the global hits view.
                // Start where the last stretch in this segment ended.
                long length = hitsInThisSegment.size() - lastStretchEnd;
                assert length > 0;
                synchronized (HitsFromQueryKeepSegments.this) {
                    HitsStretch stretch = new HitsStretch(stretches.size(), hitsInThisSegment,
                            lastStretchEnd, numberOfHits, length);
                    stretches.add(stretch);
                    lastStretchEnd += length;
                    numberOfHits += length;

                    // Add hitToStretchMappings for the appropriate indexes, so we can quickly find the stretch
                    // for a global hit index. (we record a mapping every HIT_INDEX_TO_STRETCH_STEP)
                    long hitsSinceLastMapping = stretch.globalStart % HIT_INDEX_TO_STRETCH_STEP;
                    if (hitsSinceLastMapping == 0)
                        hitsSinceLastMapping = HIT_INDEX_TO_STRETCH_STEP;
                    long nextMappingIndex = stretch.globalStart + (HIT_INDEX_TO_STRETCH_STEP - hitsSinceLastMapping);
                    while (nextMappingIndex < stretch.globalStart + length) {
                        // Add an entry for this global hit index, so we can quickly find the stretch it belongs to.
                        hitToStretchMapping.add(stretches.size() - 1);
                        nextMappingIndex += HIT_INDEX_TO_STRETCH_STEP;
                    }
                }
            }
        };
    }
}
