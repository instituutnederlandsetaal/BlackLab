package nl.inl.blacklab.search.results.hits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.lucene.index.LeafReaderContext;

import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hits.fetch.HitPublisher;
import nl.inl.blacklab.search.results.hits.fetch.HitSubscriber;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;

/**
 * Combines hits from several publishers into a single view.
 * <p>
 * Subscribes to hits from several publishers (i.e. one per segment, or one per distributed node)
 * and provides a stable combined view.
 */
public class HitsFromPublishers extends HitsAbstract {

    /** If lock hasn't been signalled after this time, continue anyway (and check for thread errors) */
    private static final long HITS_ADDED_TIMEOUT_MS = 100;

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

    /** If one of the publishers threw an exception, we save it here
     * (Throwable because we catch Exception and AssertionError)
     */
    private final Map<LeafReaderContext, Throwable> exceptionThrown = new HashMap<>();

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

    private final HitsContext context;

    /** Publishers we're collecting hits from */
    private final List<HitPublisher> publishers;

    /** Stores the persistent Hits object for each segment (by docBase).
     * This is a non-lazy, locking implementation of Hits.
     * Contrast that with the Hits object passed to the HitSubscriber.hits()
     * method, which is temporary and nonlocking for better performance.
     *
     * If lrc is null (i.e. this operation is not per-segment but global), we store it under key -1.
     */
    private final Map<Integer, Hits> hitsObjPerSegment = new ConcurrentHashMap<>();

    /** Hits that have been fetched.
     * CAUTION: Might be a bit larger than numHitsGlobalView because hits are added to that in batches!
     * When all hits have been fetched, the numbers will be the same.
     */
    private final ResultsStatsPassive hitsStats;

    /**
     * Number of documents in the hits that have been fetched.
     */
    private final ResultsStatsPassive docsStats;

//    private final AtomicLong count = new AtomicLong(0);

    /** How many publishers are still sending us hits/counts. When this hits 0, we're done. */
    private final AtomicInteger publishersActive = new AtomicInteger(0);

    /** Number of hits in the global view. Might lag behind hitsStats because hits are added to
     * the view in batches. */
    private long numHitsGlobalView = 0;

    private final LongAdder numHitsCounted = new LongAdder();

    /** The stretches that make up our global hits view, in order */
    private final ObjectList<HitsStretch> stretches = new ObjectArrayList<>();

    /**
     * Records the stretch index for every nth hit (n = {@link #HIT_INDEX_TO_STRETCH_STEP}).
     * The stretch for another hit index can then be found using linear search from
     * this stretch index if needed.
     */
    private final IntBigList hitToStretchMapping = new IntBigArrayBigList();

    /** How many hits we need internally (e.g. because of a getEphemeral() call) */
    final AtomicLong requestedHitsToProcess = new AtomicLong();

    /**
     * Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1
     */
    final AtomicLong requestedHitsToCount = new AtomicLong();

    /**
     * Configured upper limit of requestedHitsToProcess, to which it will always be clamped.
     */
    private final long maxHitsToProcess;

    /**
     * Configured upper limit of requestedHitsToCount, to which it will always be clamped.
     */
    private final long maxHitsToCount;

    /** Lock for waiting for hits to be available */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** Signalled whenever hits are added */
    private final Condition hitsAdded = lock.writeLock().newCondition();

    public HitsFromPublishers(List<? extends HitPublisher> publishers, SearchSettings searchSettings) {
        if (publishers.isEmpty())
            throw new IllegalArgumentException("No publishers given");
        this.publishers = new ArrayList<>(publishers);
        this.context = publishers.get(0).context().withoutLeafReaderContext();
        // Keep track of hits
        hitsStats = new ResultsStatsPassive(new ResultsStats.ResultsAwaiter() {
            @Override
            public boolean processedAtLeast(long lowerBound) {
                return sizeAtLeast(lowerBound);
            }

            @Override
            public long allProcessed() {
                return size();
            }

            @Override
            public long allCounted() {
                ensureResultsRead(-1);
                return numHitsCounted.sum();
            }
        }, searchSettings.maxHitsToProcess(), searchSettings.maxHitsToCount());
        // Keep track of documents
        docsStats = new ResultsStatsPassive(new ResultsStats.ResultsAwaiter() {
            @Override
            public boolean processedAtLeast(long lowerBound) {
                // There's no ensureDocsRead() method, so loop until the requested number of docs have been read
                // TODO: avoid busy-waiting, use lock/latch?
                while (!hitsStats.done() && docsStats.processedSoFar() < lowerBound) {
                    hitsStats.processedAtLeast(hitsStats.processedSoFar() + 1);
                }
                return docsStats.processedSoFar() >= lowerBound;
            }

            @Override
            public long allProcessed() {
                // Ensure all results have been seen
                size();
                // Return number of docs processed
                return docsStats.processedSoFar();
            }

            @Override
            public long allCounted() {
                // Ensure all results have been seen
                ensureResultsRead(-1);
                // Return number of docs counted
                return docsStats.countedSoFar();
            }
        });
        maxHitsToProcess = searchSettings.maxHitsToProcess();
        maxHitsToCount = searchSettings.maxHitsToCount();

        // Subscribe to all the publishers
        publishersActive.updateAndGet(c -> c + this.publishers.size());

        // While we're in the process of adding subscribers, don't call hitsStats.setDone() yet,
        // because even if the current subscribers are all done, we might still be adding more subscribers that aren't
        // done yet.
        AtomicBoolean stillAddingSubscribers = new AtomicBoolean(true);

        publishers.forEach(publisher -> {
            publisher.subscribe(new HitSubscriber() {

                /** Next hit index (from this publisher, i.e. index segment) that still needs to be added to the
                 *  global view. */
                long nextIndexToAddToGlobal = 0;

                @Override
                public void start(LeafReaderContext lrc, Hits results) {
                    // Save persistent hits object for this segment.
                    // We'll refer to this from the stretches that make up our global view.
                    if (results == null) {
                        // Some publishers (such as for grouping without storing hits) don't save their hits.
                        // This saves time and memory, but such publishers cannot be used with this class.
                        throw new IllegalStateException("start() called without a persistent Hits object! " +
                                "We need a publisher that saves its hits!");
                    }
                    hitsObjPerSegment.put(lrc == null ? -1 : lrc.docBase, results);
                }

                @Override
                public boolean needsMoreHits() {
                    boolean continueProcessing = sizeSoFar() < requestedHitsToProcess.get();
                    boolean continueCounting = numHitsCounted.sum() < requestedHitsToCount.get();
                    return continueProcessing || continueCounting;
                }

                @Override
                public void counted(long hitsCounted, int docsCounted) {
                    numHitsCounted.add(hitsCounted);
                    docsStats.add(0, docsCounted);
                    hitsStats.add(0, hitsCounted);
                }

                @Override
                public void hits(LeafReaderContext lrc, Hits batchHits, long batchStart, long batchEnd, int batchNumDocs,
                        long batchOffsetInTotal) {
                    // Add them to the global view?

                    // Note that we add small stretches at first, so the first page of hits is
                    // available quickly. Later, we add them in larger batches to reduce overhead.
                    // (note that we don't synchronize for numberOfHits because we don't care if we get a slightly
                    //  out of date (i.e. too small) value here)
                    long addHitsToGlobalThreshold = Math.max(STRETCH_THRESHOLD_MINIMUM,
                            Math.min(STRETCH_THRESHOLD_MAXIMUM, numHitsGlobalView / STRETCH_SIZE_DIVIDER));
                    long stretchEndIndex = batchOffsetInTotal + batchEnd;
                    addStretchIfLargeEnough(lrc, stretchEndIndex, addHitsToGlobalThreshold);
                    if (batchEnd > batchStart) {
                        docsStats.add(batchNumDocs, batchNumDocs);
                    }
                }

                @Override
                public void flush(LeafReaderContext lrc, long numPublished) {
                    // Add the final batch of hits to the segment results.
                    addStretchIfLargeEnough(lrc, numPublished, 0);
                }

                /** If we have enough, add the latest stretch of hits we've found to the global view.
                 *
                 * @param lrc index segment our hits are from
                 * @param stretchEndIndex end of the current stretch of hits (exclusive) we may want to add to the global view.
                 *           Note that this is an index for the current publisher's hits, i.e. the segment index, not
                 *           the global index.
                 * @param stretchLengthTreshold if the stretch we have is larger than this, add it to the global view.
                 */
                private void addStretchIfLargeEnough(LeafReaderContext lrc, long stretchEndIndex, long stretchLengthTreshold) {
                    long stretchStartIndex = nextIndexToAddToGlobal;
                    long stretchLength = stretchEndIndex - stretchStartIndex;
                    if (stretchLength > stretchLengthTreshold) {
                        addStretchFromSegment(lrc, stretchStartIndex, stretchEndIndex);
                        hitsStats.add(stretchLength, stretchLength);
                        nextIndexToAddToGlobal = stretchEndIndex;
                    }
                }

                @Override
                public void done(LeafReaderContext lrc) {
                    lock.writeLock().lock();
                    try {
                        int activePubs = publishersActive.decrementAndGet();
                        if (activePubs < 0)
                            throw new IllegalStateException("Received more 'done' messages than publishers");
                        if (activePubs == 0 && !stillAddingSubscribers.get())
                            hitsStats.setDone();
                        // If we were waiting for more hits: wake up and see that we're done
                        hitsAdded.signalAll();
                    } finally {
                        lock.writeLock().unlock();
                    }
                }

                @Override
                public void error(LeafReaderContext lrc, Throwable exception) {
                    exceptionThrown.put(lrc, exception);
                    lock.writeLock().lock();
                    try {
                        hitsAdded.signalAll();
                    } finally {
                        lock.writeLock().unlock();
                    }
                }
            });
        });

        // We're done adding subscribers.
        // We prevented the HitsSubscriber instances from calling hitsStats.setDone() until now,
        // so we should check if it needs to be called.
        stillAddingSubscribers.set(false);
        if (publishersActive.get() == 0)
            hitsStats.setDone();

    }

    /** Return publishers per segment (if available) */
    public List<HitPublisher> publishersPerSegment() {
        return Collections.unmodifiableList(publishers);
    }

    @Override
    public HitsContext context() {
        return context;
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
    private HitsStretch getHitsStretch(long index) {
        lock.readLock().lock();
        try {
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
        } finally {
            lock.readLock().unlock();
        }
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
        size(); // fetch all hits
        return this;
    }

    @Override
    public Hits sublist(long start, long length) {
        if (length == 0)
            return Hits.empty(context());
        ensureResultsRead(start + length);
        lock.readLock().lock();
        try {
            long end = start + length;
            synchronized (this) {
                if (end > numHitsGlobalView)
                    end = numHitsGlobalView;
            }
            if (start == end)
                return Hits.empty(context());
            if (start < 0 || end < 0 || start > end)
                throw new IndexOutOfBoundsException("Sub-list start " + start + " with length " + length +
                        " is out of bounds (size: " + size() + ")");

            HitsMutable sublist = HitsMutable.create(context(),
                    end - start, false, false);
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
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Iterator<EphemeralHit> iterator() {
        return new Iterator<>() {

            /**
             * Iterate over all stretches so we can iterate over each hit within them.
             * Note that this needs to be an index, not an Iterator, because we could add
             * to stretches while iterating.
             */
            int stretchIndex = -1;

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
                lock.readLock().lock();
                try {
                    while (currentStretch == null || !currentStretch.containsGlobalIndex(globalIndex)) {
                        // We need to find the stretch for this hit index.
                        stretchIndex++;
                        currentStretch = stretches.get(stretchIndex);
                    }
                    currentStretch.segmentHits().getEphemeral(currentStretch.globalToSegmentIndex(globalIndex), hit);
                    hit.convertDocIdToGlobal(currentStretch.docBase);
                    return hit;
                } finally {
                    lock.readLock().unlock();
                }
            }
        };
    }

    private void addStretchFromSegment(LeafReaderContext lrc, long from, long to) {
        lock.writeLock().lock();
        try {
            // Create a new stretch for the global hits view.
            // Start where the last stretch in this segment ended.
            long stretchLength = to - from;
            assert stretchLength > 0;
            Hits segmentHits2 = hitsObjPerSegment.get(lrc == null ? -1 : lrc.docBase);
            HitsStretch stretch = new HitsStretch(
                    stretches.size(), lrc == null ? 0 : lrc.docBase,
                    segmentHits2, from, numHitsGlobalView, stretchLength);
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
            hitsAdded.signalAll();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @SuppressWarnings("java:S899") // we don't check hitsAdded.await() return value, see below
    private boolean ensureResultsRead(long number) {
        checkException();
        if (number == 0)
            return true;
        // clamp number to [current requested, number, max. requested], defaulting to max if number < 0
        final long clampedNumber = number < 0 ? maxHitsToCount : Math.min(number, maxHitsToCount);

        // NOTE: we first update to process, then to count. If we do it the other way around, and spansReaders
        //       are running, they might check in between the two statements and conclude that they don't need to save
        //       hits anymore, only count them.
        requestedHitsToProcess.getAndUpdate(
                c -> Math.max(Math.min(clampedNumber, maxHitsToProcess), c)); // update process
        requestedHitsToCount.getAndUpdate(c -> Math.max(clampedNumber, c)); // update count

        try {
            lock.writeLock().lock();
            try {
                while (exceptionThrown.isEmpty() && !hitsStats.done() && (sizeSoFar() < requestedHitsToProcess.get() || hitsStats.countedSoFar() < requestedHitsToCount.get())) {

                    // Make sure publishers are running
                    publishers.forEach(HitPublisher::activate);

                    // Wait until some hits are added to check again
                    // We don't check the return value because we intend to do the same thing regardless of the outcome.
                    // (write lock is automatically released while waiting)
                    hitsAdded.await(HITS_ADDED_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                }
                checkException();
                return hitsStats.processedSoFar() >= number;
            } finally {
                lock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedSearch(e);
        }
    }

    private void checkException() {
        if (!exceptionThrown.isEmpty())
            throw new RuntimeException("One of the hit publishers failed", exceptionThrown.values().iterator().next());
    }

    public ResultsStats resultsStats() {
        return hitsStats;
    }

    public ResultsStats docsStats() {
        return docsStats;
    }

}
