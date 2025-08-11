package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.index.LeafReaderContext;

import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import it.unimi.dsi.fastutil.objects.ObjectBigList;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/**
 * A version of HitsFromQuery that keeps the hits from each segment separate.
 * This is useful in case we want to sort or group, which is more efficiently done per-segment, then merged.
 * We also support a global view of the unsorted hits (even while they're being fetched), for operations
 * that don't require all hits, such as returning a page of hits.
 */
public class HitsFromQueryKeepSegments extends HitsFromQuery {

    /** The hits per segment. */
    private final List<HitsInternalMutable> segmentHits = new ArrayList<>();

    /** A stretch of hits from the segment hits to construct the global view with
     */
    class HitsStretch {
        /** Number of the segment this stretch is from. */
        int segNumber;

        /** Start index in the segment hits. */
        long segStart;

        /** End index (exclusive) in the segment hits. */
        long segEnd;

        /** Start index in the global hits view. */
        long globalStart;

        public HitsStretch(int segNumber, long segStart, long segEnd, long globalStart) {
            this.segNumber = segNumber;
            this.segStart = segStart;
            this.segEnd = segEnd;
            this.globalStart = globalStart;
        }

        public long length() {
            return segEnd - segStart;
        }

        HitsInternalMutable segment() {
            return segmentHits.get(segNumber);
        }
    }

    /** For each global hit index, points to the stretch it's part of. */
    private final ObjectBigList<HitsStretch> hitIndexToStretch = new ObjectBigArrayBigList<>();

    /** The stretches that make up our global hits view, in order */
    private final List<HitsStretch> stretches = new ArrayList<>();

    protected HitsFromQueryKeepSegments(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        // NOTE: we explicitly construct HitsInternal so they're writeable
        super(queryInfo, sourceQuery, searchSettings);
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
                return hitIndexToStretch.size64();
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

            private HitsStretch getHitsStretch(long index) {
                // Find the stretch this hit is part of.
                HitsStretch stretch = hitIndexToStretch.get(index);
                if (stretch == null) {
                    throw new IndexOutOfBoundsException("Hit index " + index + " is out of bounds (size: " + size() + ")");
                }
                return stretch;
            }

            private static long segmentIndex(long index, HitsStretch stretch) {
                // Calculate the index of this global hit in the segment's hits.
                long indexInSegment = index - stretch.globalStart + stretch.segStart;
                assert indexInSegment >= 0 : "Index in segment out of bounds: " + indexInSegment;
                return indexInSegment;
            }

            @Override
            public void getEphemeral(long index, EphemeralHit hit) {
                ensureResultsRead(index + 1);
                HitsStretch stretch = getHitsStretch(index);
                stretch.segment().getEphemeral(segmentIndex(index, stretch), hit);
            }

            @Override
            public int doc(long index) {
                ensureResultsRead(index + 1);
                HitsStretch stretch = getHitsStretch(index);
                return stretch.segment().doc(segmentIndex(index, stretch));
            }

            @Override
            public int start(long index) {
                ensureResultsRead(index + 1);
                HitsStretch stretch = getHitsStretch(index);
                return stretch.segment().start(segmentIndex(index, stretch));
            }

            @Override
            public int end(long index) {
                ensureResultsRead(index + 1);
                HitsStretch stretch = getHitsStretch(index);
                return stretch.segment().end(segmentIndex(index, stretch));
            }

            @Override
            public MatchInfo[] matchInfos(long hitIndex) {
                ensureResultsRead(hitIndex + 1);
                HitsStretch stretch = getHitsStretch(hitIndex);
                return stretch.segment().matchInfos(segmentIndex(hitIndex, stretch));
            }

            @Override
            public MatchInfo matchInfo(long hitIndex, int matchInfoIndex) {
                ensureResultsRead(hitIndex + 1);
                HitsStretch stretch = getHitsStretch(hitIndex);
                return stretch.segment().matchInfo(segmentIndex(hitIndex, stretch), matchInfoIndex);
            }

            @Override
            public HitsSimple getStatic() {
                return this;
            }

            @Override
            public HitsSimple sublist(long start, long windowSize) {
                ensureResultsRead(start + windowSize);
                long end = start + windowSize;
                if (end > size())
                    end = size();
                if (start < 0 || end < 0 || start > end)
                    throw new IndexOutOfBoundsException("Window start " + start + " with size " + windowSize +
                            " is out of bounds (size: " + size() + ")");
                HitsInternalMutable window = HitsInternal.create(field(), matchInfoDefs(), end - start, false, false);
                EphemeralHit h = new EphemeralHit();
                for (long i = start; i < end; ++i) {;
                    getEphemeral(i, h);
                    window.add(h);
                }
                return window;
            }

            @Override
            public Iterator<EphemeralHit> ephemeralIterator() {
                return new Iterator<>() {

                    long index = -1;

                    final EphemeralHit hit = new EphemeralHit();

                    @Override
                    public boolean hasNext() {
                        return index + 1 < size();
                    }

                    @Override
                    public EphemeralHit next() {
                        if (!hasNext())
                            throw new IndexOutOfBoundsException("No more hits available (index: " + index + ")");
                        index++;
                        getEphemeral(index, hit);
                        return hit;
                    }
                };
            }

            @Override
            public HitsSimple sorted(HitProperty sortProp) {
                ensureResultsRead(-1);

                // FIXME: sort per segment (in parallel), then merge - separate class
                HitsInternalMutable sortedHits = HitsInternal.create(field(), matchInfoDefs(), size(), size(), false);
                for (HitsInternalMutable h: segmentHits) {
                    sortedHits.addAll(h);
                }
                return sortedHits.sorted(sortProp);
            }

            @Override
            public boolean hasMatchInfo() {
                return hitQueryContext.numberOfMatchInfos() > 0;  // correct...?
            }
        };
    }

    /**
     * How many hits should we collect (at least) before we add them to the global results?
     */
    private static final int ADD_HITS_TO_GLOBAL_THRESHOLD = 100;

    protected SpansReader.Strategy getSpansReaderStrategy(LeafReaderContext lrc) {
        // We'll collect the segment hits here. It has to lock, because we'll be writing and reading from
        // it at the same time.
        final HitsInternalMutable collectHits = HitsInternal.create(field(), hitQueryContext.getMatchInfoDefs(),
                -1, true, true);
        final int segmentNumber = segmentHits.size();
        segmentHits.add(collectHits);

        return new SpansReader.Strategy() {

            long lastStretchEnd = 0; // last stretch end index in the global hits view

            @Override
            public void onDocumentBoundary(HitsInternalMutable results) {
                // We've built up a batch of hits. Add them to the global results.
                // We do this only once per doc, so hits from the same doc remain contiguous in the master list.
                synchronized (collectHits) {
                    collectHits.addAll(results);
                    if (collectHits.size() > ADD_HITS_TO_GLOBAL_THRESHOLD) {
                        addStretch();
                    }
                }
                results.clear();
            }

            @Override
            public void onFinished(HitsInternalMutable results) {
                // Add the final batch of hits to the segment results.
                synchronized (collectHits) {
                    collectHits.addAll(results);
                    if (!collectHits.isEmpty())
                        addStretch();
                }
            }

            private void addStretch() {
                // Create a new stretch for the global hits view.
                // Start where the last stretch in this segment ended.
                HitsStretch stretch = new HitsStretch(segmentNumber, lastStretchEnd,
                        collectHits.size(), hitIndexToStretch.size64());
                stretches.add(stretch);
                for (long i = 0; i < stretch.length(); i++) {
                    // For each hit in this stretch, remember which stretch it belongs to.
                    hitIndexToStretch.add(stretch);
                }
                lastStretchEnd = stretch.segEnd;
            }
        };
    }
}
