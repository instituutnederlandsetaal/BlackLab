package nl.inl.blacklab.search.results.hits;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;

import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.hits.fetch.HitCollector;
import nl.inl.blacklab.search.results.hits.fetch.HitCollectorSegment;
import nl.inl.blacklab.search.results.hits.fetch.HitFetcher;
import nl.inl.blacklab.search.results.hits.fetch.HitFilter;
import nl.inl.blacklab.search.results.stats.ResultsStats;

/**
 * Our main hit fetching class.
 * <p>
 * Fetches hits from a hit fetcher per segment in parallel and provides
 * a stable global view of the lists of segment hits.
 */
public class HitsFromFetcher extends HitsAbstract implements HitCollector {

    /**
     * The step with which hitToStretchMapping records mappings.
     * <p>
     * hitToStretchMapping only has a value for every nth hit index in the
     * global view. So hitToStretchMapping.getInt(i) will return the stretch
     * for hit index i * HIT_INDEX_TO_STRETCH_STEP.
     * <p>
     * Larger values make the global hits view a bit slower,
     * but gathering hits a bit faster.
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
    private static final int STRETCH_THRESHOLD_MAXIMUM = 10000;

    /**
     * Divider for the size of new stretches.
     * <p>
     * For efficiency, we make new stretches a fraction of the total number of hits so far,
     * so the size of new stretches grows as the number of hits retrieved grows.
     */
    private static final int STRETCH_SIZE_DIVIDER = 10;

    private final AnnotatedField field;

    public HitFetcher hitFetcher;

    /** Hits that have been fetched.
     * CAUTION: Might be a bit larger than numHitsGlobalView because hits are added to that in batches!
     * When all hits have been fetched, the numbers will be the same.
     */
    private final ResultsStats hitsStats;

    /**
     * Number of documents in the hits that have been fetched.
     */
    private final ResultsStats docsStats;

    /** The hits per segment. */
    private Map<LeafReaderContext, Hits> hitsPerSegment = new LinkedHashMap<>();

    /** Number of hits in the global view. Might lag behind hitsStats because hits are added to
     * the view in batches. */
    private long numHitsGlobalView = 0;

    /** The stretches that make up our global hits view, in order */
    private final ObjectList<HitsStretch> stretches = new ObjectArrayList<>();

    /**
     * Records the stretch index for every nth hit (n = {@link #HIT_INDEX_TO_STRETCH_STEP}).
     * The stretch for another hit index can then be found using linear search from
     * this stretch index if needed.
     */
    private final IntBigList hitToStretchMapping = new IntBigArrayBigList();

    public HitsFromFetcher(HitFetcher hitFetcher, HitFilter filter) {
        if (filter == null)
            throw new IllegalArgumentException("filter cannot be null");
        this.field = hitFetcher.field();
        this.hitFetcher = hitFetcher;
        hitsStats = hitFetcher.hitsStats();
        docsStats = hitFetcher.docsStats();
        hitFetcher.fetchHits(filter, this);
    }

    @Override
    public AnnotatedField field() {
        return field;
    }

    @Override
    public MatchInfoDefs matchInfoDefs() {
        return hitFetcher.getHitQueryContext().getMatchInfoDefs();
    }

    @Override
    public long size() {
        ensureResultsRead(-1);
        return numHitsGlobalView;
    }

    @Override
    public long sizeSoFar() {
        return numHitsGlobalView;
    }

    @Override
    public boolean sizeAtLeast(long minSize) {
        return ensureResultsRead(minSize);
    }

    /**
     * Get the stretch a certain hit is part of
     */
    private synchronized HitsStretch getHitsStretch(long index) {
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

    @Override
    public void getEphemeral(long index, EphemeralHit hit) {
        ensureResultsRead(index + 1);
        HitsStretch stretch = getHitsStretch(index);
        stretch.segmentHits().getEphemeral(stretch.globalToSegmentIndex(index), hit);
        hit.convertDocIdToGlobal(stretch.docBase);
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
    public Hits getStatic() {
        // Making a static copy of the entire global hits view is too expensive.
        // Just return this object. Ideally, we should be using the per-segment hits anyway.
        return this;
    }

    @Override
    public Hits sublist(long start, long length) {
        if (length == 0)
            return Hits.empty(field(), matchInfoDefs());
        ensureResultsRead(start + length);
        long end = start + length;
        synchronized (this) {
            if (end > numHitsGlobalView)
                end = numHitsGlobalView;
        }
        if (start == end)
            return Hits.empty(field(), matchInfoDefs());
        if (start < 0 || end < 0 || start > end)
            throw new IndexOutOfBoundsException("Sub-list start " + start + " with length " + length +
                    " is out of bounds (size: " + size() + ")");

        HitsMutable sublist = HitsMutable.create(field(), matchInfoDefs(), end - start, false, false);
        EphemeralHit h = new EphemeralHit();
        long globalIndex = start;
        HitsStretch currentStretch = getHitsStretch(globalIndex);
        long indexInSegment = currentStretch.globalToSegmentIndex(globalIndex);
        if (indexInSegment < 0)
            throw new IllegalStateException("Negative index in segment: " + indexInSegment +
                    " (global index: " + globalIndex + ", stretch: " + currentStretch + ")");
        if (indexInSegment >= currentStretch.segmentHits().size())
            throw new IllegalStateException("Index in segment out of bounds: " + indexInSegment +
                    " (global index: " + globalIndex + ", stretch: " + currentStretch + ")");
        long hitsLeftInStretch = currentStretch.stretchLength - (globalIndex - currentStretch.firstHitGlobal);
        while (true) {
            // Add a hit from the current stretch to the sublist.
            currentStretch.segmentHits().getEphemeral(indexInSegment, h);
            h.convertDocIdToGlobal(currentStretch.docBase);
            sublist.add(h);
            indexInSegment++;

            // Are we done?
            globalIndex++;
            if (globalIndex == end)
                break;

            // If we reached the end of the current stretch, go to the next stretch.
            hitsLeftInStretch--;
            if (hitsLeftInStretch == 0) {
                synchronized (this) {
                    currentStretch = stretches.get(currentStretch.stretchIndex + 1);
                }
                indexInSegment = currentStretch.globalToSegmentIndex(globalIndex);
                hitsLeftInStretch = currentStretch.stretchLength - (globalIndex - currentStretch.firstHitGlobal);
            }
        }
        return sublist;
    }

    @Override
    public Iterator<EphemeralHit> iterator() {
        return new Iterator<>() {

            /** Iterate over all stretches so we can iterate over each hit within them */
            final Iterator<HitsStretch> stretchIterator = stretches.iterator();

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
                hit.convertDocIdToGlobal(currentStretch.docBase);
                return hit;
            }
        };
    }

    private synchronized void registerSegment(LeafReaderContext lrc, Hits segmentHits) {
        hitsPerSegment.put(lrc, segmentHits); // only called from constructor, so no need to synchronize
    }

    private synchronized void addStretchFromSegment(LeafReaderContext lrc, Hits segmentHits, long segmentIndexStart) {
        // Create a new stretch for the global hits view.
        // Start where the last stretch in this segment ended.
        long stretchLength = segmentHits.size() - segmentIndexStart;
        assert stretchLength > 0;
        HitsStretch stretch = new HitsStretch(
                stretches.size(), lrc == null ? 0 : lrc.docBase,
                segmentHits, segmentIndexStart, numHitsGlobalView, stretchLength);
        stretches.add(stretch);
        numHitsGlobalView += stretchLength;

        // Add hitToStretchMappings for the appropriate indexes, so we can quickly find the stretch
        // for a global hit index. (we record a mapping every HIT_INDEX_TO_STRETCH_STEP)
        long hitsSinceLastMapping = stretch.firstHitGlobal % HIT_INDEX_TO_STRETCH_STEP;
        if (hitsSinceLastMapping == 0)
            hitsSinceLastMapping = HIT_INDEX_TO_STRETCH_STEP;
        long nextMappingIndex = stretch.firstHitGlobal + (HIT_INDEX_TO_STRETCH_STEP - hitsSinceLastMapping);
        while (nextMappingIndex < stretch.firstHitGlobal + stretchLength) {
            // Add an entry for this global hit index, so we can quickly find the stretch it belongs to.
            hitToStretchMapping.add(stretches.size() - 1);
            nextMappingIndex += HIT_INDEX_TO_STRETCH_STEP;
        }
    }

    public long globalHitsSoFar() {
        return numHitsGlobalView;
    }

    public boolean ensureResultsRead(long number) {
        return hitFetcher.ensureResultsRead(number);
    }

    public ResultsStats resultsStats() {
        return hitsStats;
    }
    
    public ResultsStats docsStats() {
        return docsStats;
    }

    @Override
    public Map<LeafReaderContext, Hits> hitsPerSegment() {
        if (resultsStats().done()) {
            // All hits have been fetched, so hitsPerSegment is complete.
            return Collections.unmodifiableMap(hitsPerSegment);
        } else {
            // @@@ TODO we're still fetching, so we need to return "lazy" implementations that will block
            //  until enough hits are available
            return Collections.unmodifiableMap(hitsPerSegment);
        }
    }

    public HitCollectorSegment getSegmentCollector(LeafReaderContext lrc) {
        return new HitCollectorSegment() {

            /** The list of hits in this segment to add new hits to */
            private final HitsMutable segmentHits;

            /** Number of hits already added to the global view.
             * <p>
             * Therefore also the first hit index that's not yet part of the global view.
             */
            long numberAddedToGlobalView;

            {
                // We'll collect the segment hits here. It has to lock, because we'll be writing and reading from it.
                segmentHits = HitsMutable.create(field(), matchInfoDefs(), -1, true, true);
                registerSegment(lrc, segmentHits);
                numberAddedToGlobalView = 0;
            }

            @Override
            public void onDocumentBoundary(HitsMutable results) {
                // Add new hits to the segment results.
                segmentHits.addAll(results);

                // Add them to the global view? We only do this on a boundary
                // between documents, so hits from the same doc remain
                // contiguous in the global view.

                // Note that we add small stretches at first, so the first page of hits is
                // available quickly. Later, we add them in larger batches to reduce overhead.
                // (note that we don't synchronize for numberOfHits because we don't care if we get a slightly
                //  out of date (i.e. too small) value here)
                long addHitsToGlobalThreshold = Math.max(STRETCH_THRESHOLD_MINIMUM,
                        Math.min(STRETCH_THRESHOLD_MAXIMUM, numHitsGlobalView / STRETCH_SIZE_DIVIDER));
                addStretchIfLargeEnough(addHitsToGlobalThreshold);
            }

            @Override
            public void flush() {
                // Add the final batch of hits to the segment results.
                addStretchIfLargeEnough(0);
            }

            /** Add the latest stretch of hits we've found to the global view. */
            private void addStretchIfLargeEnough(long threshold) {
                if (segmentHits.size() - numberAddedToGlobalView > threshold) {
                    addStretchFromSegment(lrc, segmentHits, numberAddedToGlobalView);
                    numberAddedToGlobalView = segmentHits.size();
                }
            }
        };
    }

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
        Hits segmentHits;

        /** Start index in the segment hits. */
        long firstHitSegment;

        /** Start index in the global hits view. */
        long firstHitGlobal;

        /** Length of this stretch. */
        long stretchLength;

        public HitsStretch(int stretchIndex, int docBase, Hits segmentHits, long firstHitSegment,
                long firstHitGlobal, long stretchLength) {
            this.stretchIndex = stretchIndex;
            this.docBase = docBase;
            this.segmentHits = segmentHits;
            this.firstHitSegment = firstHitSegment;
            this.firstHitGlobal = firstHitGlobal;
            this.stretchLength = stretchLength;
        }

        /** Get the associated segment's hits */
        Hits segmentHits() {
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
            assert indexInSegment < segmentHits.size() :
                    "Index in segment out of bounds: " + indexInSegment + " (segment size: " + segmentHits.size() + ")";
            return indexInSegment;
        }

        @Override
        public String toString() {
            return "HitsStretch{" +
                    "docBase=" + docBase +
                    ", stretchIndex=" + stretchIndex +
                    ", segHits=" + segmentHits.size() +
                    ", segStart=" + firstHitSegment +
                    ", globalStart=" + firstHitGlobal +
                    ", length=" + stretchLength +
                    "}";
        }
    }
}
