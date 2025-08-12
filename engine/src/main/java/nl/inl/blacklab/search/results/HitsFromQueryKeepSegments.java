package nl.inl.blacklab.search.results;

import java.util.Iterator;

import org.apache.lucene.index.LeafReaderContext;

import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import nl.inl.blacklab.resultproperty.HitProperty;
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
     * How long a stretch of segment hits in the global view should ideally be.
     * <p>
     * Note that we DO sometimes add stretches smaller than this if we happen to be done
     * collecting hits for now and want to add the last hits to the global view.
     */
    private static final int STRETCH_SIZE_TRY_MINIMUM = 20;

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
                    '}';
        }
    }

    /** The hits per segment. */
    private ObjectList<HitsInternalMutable> hitsPerSegment;

    /** Number of hits in the global view. */
    protected long numberOfHits = 0;

    /** The stretches that make up our global hits view, in order */
    private final ObjectList<HitsStretch> stretches = new ObjectArrayList<>();

    /** Records the stretch index for every nth hit (n = {@link #HIT_INDEX_TO_STRETCH_STEP}) */
    private final IntBigList hitToStretchMapping = new IntBigArrayBigList();

    protected HitsFromQueryKeepSegments(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        // NOTE: we explicitly construct HitsInternal so they're writeable
        super(queryInfo, sourceQuery, searchSettings);
        if (hitsPerSegment == null) {
            // (can only happen if there are no spans - normally initialized in getSpansReaderStrategy())
            hitsPerSegment = new ObjectArrayList<>();
        }
    }

    /** Number of hits in the global view. Needed because we don't want to call hitsInternalMutable.size() from
     *  HitsFromQueryKeepSegments, because that class doesn't use it. (REFACTOR THIS!) */
    protected long globalHitsSoFar() {
        return numberOfHits;
    }

    @Override
    public HitsSimple getHits() {
        return new HitsSimple() {

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

            @Override
            public HitsSimple sorted(HitProperty sortProp) {
                ensureResultsRead(-1);

                // NOTE: this method is provided for completeness, but it is not efficient.
                //       the better way is to sort the hits per segment in parallel, then merge them.
                HitsInternalMutable sortedHits = HitsInternal.create(field(), matchInfoDefs(), size(), size(), false);
                for (HitsInternalMutable h: hitsPerSegment) {
                    sortedHits.addAll(h);
                }
                return sortedHits.sorted(sortProp);
            }

            @Override
            public boolean hasMatchInfo() {
                return hitQueryContext.numberOfMatchInfos() > 0;
            }
        };
    }

    protected SpansReader.Strategy getSpansReaderStrategy(LeafReaderContext lrc) {
        // We'll collect the segment hits here. It has to lock, because we'll be writing and reading from
        // it at the same time.
        final HitsInternalMutable hitsInThisSegment = HitsInternal.create(field(), hitQueryContext.getMatchInfoDefs(),
                -1, true, true);
        if (hitsPerSegment == null)
            hitsPerSegment = new ObjectArrayList<>();
        hitsPerSegment.add(hitsInThisSegment); // only called from constructor, so no need to synchronize
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
                long addHitsToGlobalThreshold = Math.max(STRETCH_SIZE_TRY_MINIMUM, numberOfHits / STRETCH_SIZE_DIVIDER);
                if (hitsInThisSegment.size() - lastStretchEnd > addHitsToGlobalThreshold) {
                    addStretch();
                }
                results.clear();
            }

            @Override
            public void onFinished(HitsInternalMutable results) {
                // Add the final batch of hits to the segment results.
                hitsInThisSegment.addAll(results);
                if (hitsInThisSegment.size() - lastStretchEnd > 0) {
                    addStretch();
                }
            }

            /** Add the latest stretch of hits we've found to the global view. */
            private void addStretch() {
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
