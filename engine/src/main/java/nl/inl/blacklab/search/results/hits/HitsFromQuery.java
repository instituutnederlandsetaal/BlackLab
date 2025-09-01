package nl.inl.blacklab.search.results.hits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.util.PriorityQueue;

import com.ibm.icu.text.CollationKey;

import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyContextBase;
import nl.inl.blacklab.resultproperty.PropContext;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.results.QueryTimings;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hitresults.ResultsAwaitable;
import nl.inl.blacklab.search.results.hitresults.ResultsAwaiterDocs;
import nl.inl.blacklab.search.results.hitresults.ResultsAwaiterHits;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;

/**
 * Our main hit fetching class.
 * <p>
 * Fetches hits from a query per segment in parallel and provides
 * a stable global view of the lists of segment hits.
 */
public class HitsFromQuery extends HitsAbstract implements ResultsAwaitable {

    private static final Logger logger = LogManager.getLogger(HitsFromQuery.class);

    /** If another thread is busy fetching hits and we're monitoring it, how often should we check? */
    private static final int HIT_POLLING_TIME_MS = 50;

    /**
     * Minimum number of hits to fetch in an ensureHitsRead() block.
     * <p>
     * This prevents locking again and again for a single hit when iterating.
     */
    private static final int FETCH_HITS_MIN = 20;

    /**
     * The step with which hitToStretchMapping records mappings.
     * <p>
     * hitToStretchMapping only has a value for every nth hit index in the
     * global view. So hitToStretchMapping.getInt(i) will return the stretch
     * for hit index i * HIT_INDEX_TO_STRETCH_STEP.
     *
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

    /**
     * Testing reveals this to be a good number of threads for fetching hits in parallel.
     * More is not useful, and we can afford to use 4 threads as fetching hits is a very fast operation
     * compared to sort/group.
     */
    public static final int IDEAL_NUM_THREADS_FETCHING = 4;

    /**
     * Grouping hits is best done in 3 threads on average.
     * Depending on the exact search, 2 or 4 might be a bit better, but that's hard to predict.
     */
    private static final int IDEAL_NUM_THREADS_GROUPING = 3;

    /**
     * Below this number of hits, we'll sort/group in a single thread to save overhead.
     */
    private static int THRESHOLD_SINGLE_THREADED = 100; //1_000_000_000;

    public static void setThresholdSingleThreadedGroupAndSort(int n) {
        THRESHOLD_SINGLE_THREADED = n;
    }

    private final AnnotatedField field;
    
    private final MatchInfoDefs matchInfoDefs;

    private final QueryTimings timings;

    /** Max. number of threads to use for fetch, sort, group. */
    private final int maxThreadsPerOperation;

    /** Configured upper limit of requestedHitsToProcess, to which it will always be clamped. */
    private final long maxHitsToProcess;

    /** Configured upper limit of requestedHitsToCount, to which it will always be clamped. */
    private final long maxHitsToCount;

    /** Query context, keeping track of e.g. match info defitions */
    private final HitQueryContext hitQueryContext;

    /** Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1 */
    private final AtomicLong requestedHitsToProcess = new AtomicLong();

    /** Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1 */
    private final AtomicLong requestedHitsToCount = new AtomicLong();

    /** Used to make sure that only 1 thread can be fetching hits at a time. */
    private final Lock ensureHitsReadLock = new ReentrantLock();

    /** If true, we're done. */
    private boolean allSourceSpansFullyRead = false;

    /** Objects getting the actual hits from each index segment and adding them to the global results list. */
    final List<SpansReader> spansReaders = new ArrayList<>();

    private final ResultsStatsPassive hitsStats;

    private final ResultsStatsPassive docsStats;

    /** The hits per segment. */
    private Map<LeafReaderContext, HitsMutable> hitsPerSegment;

    /** Number of hits in the global view. */
    private long numHitsGlobalView = 0;

    /** The stretches that make up our global hits view, in order */
    private final ObjectList<HitsStretch> stretches = new ObjectArrayList<>();

    /**
     * Records the stretch index for every nth hit (n = {@link #HIT_INDEX_TO_STRETCH_STEP}).
     * The stretch for another hit index can then be found using linear search from
     * this stretch index if needed.
     */
    private final IntBigList hitToStretchMapping = new IntBigArrayBigList();

    public HitsFromQuery(AnnotatedField field, QueryTimings timings, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        this.field = field;
        this.timings = timings;
        maxHitsToProcess = searchSettings.maxHitsToProcess();
        maxHitsToCount = searchSettings.maxHitsToCount();
        hitsStats = new ResultsStatsPassive(new ResultsAwaiterHits(this),
                searchSettings.maxHitsToProcess(),
                searchSettings.maxHitsToCount());
        docsStats = new ResultsStatsPassive(new ResultsAwaiterDocs(this));
        hitQueryContext = new HitQueryContext(field.index(), null, field); // each spans will get a copy
        maxThreadsPerOperation = Math.max(field.index().blackLab().maxThreadsPerSearch(), 1);

        this.matchInfoDefs = hitQueryContext.getMatchInfoDefs();
        hitsPerSegment = new LinkedHashMap<>();

        BLSpanWeight weight = rewriteAndCreateWeight(sourceQuery, searchSettings.fiMatchFactor());
        for (LeafReaderContext leafReaderContext: index().reader().leaves()) {
            spansReaders.add(new SpansReader(
                    weight,
                    leafReaderContext,
                    this.hitQueryContext,
                    new GlobalViewIntoSegmentHitsStrategy(leafReaderContext),
                    this.requestedHitsToProcess,
                    this.requestedHitsToCount,
                    hitsStats,
                    docsStats
            ));
        }

        if (spansReaders.isEmpty())
            setDone();
    }

    /***
     * Make equal groups of items, so that each group has approximately the same total size.
     * This is useful for distributing work evenly over multiple threads.
     *
     * @param items the items to group
     * @param sizeGetter a function that returns the size of each item, used to determine how to group them
     * @param numberOfGroups the number of groups to create
     * @return a list of groups, each group is a list of items
     * @param <T> the type of items to group
     */
    public static <T> List<List<T>> makeEqualGroups(Collection<T> itemsColl, Function<T, Long> sizeGetter, int numberOfGroups) {
        List<T> items = new ArrayList<>(itemsColl);
        items.sort(Comparator.comparing(sizeGetter).reversed());

        // Now divide the segments into groups by repeatedly adding the largest remaining segment to
        // the smallest group.
        List<List<T>> groups =
                new ArrayList<>(numberOfGroups);
        List<Long> hitsInGroup = new ArrayList<>(numberOfGroups);
        for (int i = 0; i < numberOfGroups; i++) {
            groups.add(new ArrayList<>()); // create empty group for each thread}
            hitsInGroup.add(0L);
        }
        for (T segment: items) {
            // Find the group with the least hits so far, and add this segment to that group.
            int minGroupIndex = 0;
            for (int i = 1; i < hitsInGroup.size(); i++) {
                if (hitsInGroup.get(i) < hitsInGroup.get(minGroupIndex)) {
                    minGroupIndex = i;
                }
            }
            groups.get(minGroupIndex).add(segment);
            hitsInGroup.set(minGroupIndex, hitsInGroup.get(minGroupIndex) + sizeGetter.apply(segment));
        }
        return groups;
    }

    @Override
    public AnnotatedField field() {
        return field;
    }

    @Override
    public MatchInfoDefs matchInfoDefs() {
        return hitQueryContext.getMatchInfoDefs();
    }

    @Override
    public long size() {
        ensureResultsRead(-1);
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

    private synchronized void registerSegment(LeafReaderContext lrc, HitsMutable segmentHits) {
        if (hitsPerSegment == null)
            hitsPerSegment = new LinkedHashMap<>();
        hitsPerSegment.put(lrc, segmentHits); // only called from constructor, so no need to synchronize
    }

    private synchronized void addStretchFromSegment(LeafReaderContext lrc, Hits segmentHits, long segmentIndexStart) {
        // Create a new stretch for the global hits view.
        // Start where the last stretch in this segment ended.
        long stretchLength = segmentHits.size() - segmentIndexStart;
        assert stretchLength > 0;
        HitsStretch stretch = new HitsStretch(
                stretches.size(), lrc.docBase,
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
        // clamp number to [current requested, number, max. requested], defaulting to max if number < 0
        final long clampedNumber = number < 0 ? maxHitsToCount : Math.min(number + FETCH_HITS_MIN, maxHitsToCount);

        if (allSourceSpansFullyRead || globalHitsSoFar() >= clampedNumber) {
            return globalHitsSoFar() >= number;
        }

        // NOTE: we first update to process, then to count. If we do it the other way around, and spansReaders
        //       are running, they might check in between the two statements and conclude that they don't need to save
        //       hits anymore, only count them.
        this.requestedHitsToProcess.getAndUpdate(c -> Math.max(Math.min(clampedNumber, maxHitsToProcess), c)); // update process
        this.requestedHitsToCount.getAndUpdate(c -> Math.max(clampedNumber, c)); // update count

        boolean hasLock = false;
        int numThreads = Math.min(IDEAL_NUM_THREADS_FETCHING, maxThreadsPerOperation);
        Parallel<SpansReader, Void> parallel = new Parallel<>(index(), numThreads);
        try {
            while (!ensureHitsReadLock.tryLock(HIT_POLLING_TIME_MS, TimeUnit.MILLISECONDS)) {
                /*
                 * Another thread is already working on hits, we don't want to straight up block until it's done,
                 * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction.
                 * So instead poll our own state, then if we're still missing results after that just count them ourselves
                 */
                if (allSourceSpansFullyRead || (globalHitsSoFar() >= clampedNumber)) {
                    return globalHitsSoFar() >= number;
                }
            }
            hasLock = true;

            // This is the blocking portion, start worker threads, then wait for them to finish.

            // Distribute the SpansReaders over the threads.
            // Make sure the number of documents per segment is roughly equal for each thread.
            Function<SpansReader, Long> sizeGetter = spansReader ->
                    spansReader.leafReaderContext == null ? 0 : (long) spansReader.leafReaderContext.reader().maxDoc();
            List<Future<List<Void>>> pendingResults = parallel.forEach(spansReaders, sizeGetter,
                    l -> l.forEach(SpansReader::run));

            // Wait for workers to complete.
            // This will throw InterrupedException if this (HitsFromQuery) thread is interruped while waiting.
            // NOTE: the worker will not automatically abort, so we should also interrupt our workers should that happen.
            // The workers themselves won't ever throw InterruptedException, it would be wrapped in ExecutionException.
            // (Besides, we're the only thread that can call interrupt() on our worker anyway, and we don't ever do that.
            //  Technically, it could happen if the Executor were to shut down, but it would still result in an ExecutionException anyway.)
            //
            // If we're interrupted while waiting for workers to finish, and we were the thread that created the workers,
            // cancel them. (this isn't always the case, we may have been interrupted during self-polling phase)
            // For the TermsReaders that aren't done yet, the next time this function is called we'll just create new
            // Runnables/Futures of them.
            parallel.waitForAll(pendingResults);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException rte)
                throw rte;
            else
                throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            // something unforseen happened in our thread
            // Should generally never happen unless there's a bug or something catastrophic happened.
            throw new IllegalStateException(e);
        } finally {
            // Don't do this unless we're the thread that's actually using the SpansReaders.
            if (hasLock) {
                // Remove all SpansReaders that have finished.
                spansReaders.removeIf(spansReader -> spansReader.isDone);
                if (spansReaders.isEmpty())
                    setDone(); // all spans have been read, so we're done
                ensureHitsReadLock.unlock();
            }
        }
        return globalHitsSoFar() >= number;
    }

    @Override
    public ResultsStats resultsStats() {
        return hitsStats;
    }
    
    public ResultsStats docsStats() {
        return docsStats;
    }

    public MatchInfoDefs getMatchInfoDefs() {
        return hitQueryContext.getMatchInfoDefs();
    }

    /**
     * The state of a segment during a merge
     */
    static class SegmentInMerge {

        /**
         * Hits from this segment
         */
        Hits hits;

        /**
         * Segment's docBase, for converting segment doc id to global doc id
         */
        int docBase;

        /**
         * Property to sort on
         */
        HitProperty sortBy;

        /**
         * Index of the next hit from this segment to merge
         */
        int index;

        public SegmentInMerge(SegmentHits segmentResults, HitProperty sortBy) {
            this.hits = segmentResults.hits;
            this.docBase = segmentResults.lrc.docBase;

            // We need global sort values (term/doc ids) for the merge operation.
            this.sortBy = sortBy.copyWith(PropContext.segmentToGlobal(hits, segmentResults.lrc));
            this.index = 0;
        }

        boolean done() {
            return index >= hits.size();
        }

        PropertyValue sortValue() {
            return sortBy.get(index);
        }

        void getHitAndAdvance(EphemeralHit hit) {
            hits.getEphemeral(index, hit);
            index++;
        }
    }

    /**
     * Priority queue for merging n already-sorted lists of hits.
     */
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

    /**
     * Hits from a single segment
     */
    record SegmentHits(
            LeafReaderContext lrc,
            Hits hits) {
    }

    @Override
    public Map<PropertyValue, Group> grouped(HitProperty groupBy, long maxValuesToStorePerGroup) {
        logger.debug("GROUP: fetch all hits");
        // Fetch all the hits first.
        ensureResultsRead(-1);

        // If there are only a few hits, just group them in a single thread.
        if (maxThreadsPerOperation == 1 || numHitsGlobalView < THRESHOLD_SINGLE_THREADED) {
            logger.debug("GROUP: single thread");

            Map<PropertyValue, Group> groups = new HashMap<>();
            for (Map.Entry<LeafReaderContext, HitsMutable> entry: hitsPerSegment.entrySet()) {
                groupHits(entry.getValue(), groupBy.copyWith(PropContext.globalHits(entry.getValue(), new HashMap<>())), maxValuesToStorePerGroup, groups, entry.getKey());
            }
            logger.debug("GROUP: single thread finished");
            return groups;
        }

        logger.debug("GROUP: launch threads");

        // Group in parallel and merge the results.
        boolean isContext = groupBy instanceof HitPropertyContextBase;
        int numThreads = Math.min(maxThreadsPerOperation, IDEAL_NUM_THREADS_GROUPING);
        Parallel<Map.Entry<LeafReaderContext, HitsMutable>, Map<PropertyValue, Group>> parallel = new Parallel<>(index(), numThreads);
        HitProperty groupByWithCache = groupBy.copyWith(PropContext.globalHits(null, new ConcurrentHashMap<>()));
        return parallel.mapReduce(hitsPerSegment.entrySet(),
                entry -> entry.getValue().size(),
                threadItems -> {
                    int threadNum = threadItems.hashCode() % 1000;
                    logger.debug("GROUP:    a thread started: " + threadNum);
                    // Group items in these segments into a single map.
                    Map<PropertyValue, Group> groups = new HashMap<>();
                    for (Map.Entry<LeafReaderContext, HitsMutable> entry: threadItems) {
                        // For each segment, group the hits using the specified property.
                        logger.debug("GROUP:      thread " + threadNum + ", hits size " + entry.getValue().size());
                        groupHits(entry.getValue(), groupByWithCache, maxValuesToStorePerGroup, groups, entry.getKey());
                    }
                    logger.debug("GROUP:    a thread finished: " + threadNum);
                    return List.of(groups);
                },
                (acc, results) -> {
                    logger.debug("GROUP:    merging results from a thread");
                    for (Map.Entry<PropertyValue, Group> entry: results.entrySet()) {
                        PropertyValue groupId = entry.getKey();
                        Group segmentGroup = entry.getValue();
                        acc.compute(groupId, (PropertyValue k, Group v) ->
                                v == null ? segmentGroup : v.merge(segmentGroup, maxValuesToStorePerGroup));
                    }
                    logger.debug("GROUP:    merging results from a thread finished");
                },
                HashMap::new
        );
    }

    @Override
    public Hits sorted(HitProperty sortBy) {
        logger.debug("SORT: start");
        // Fetch all the hits and determine size.
        size();
//        if (numThreads == 1 || numHitsGlobalView < THRESHOLD_SINGLE_THREADED) {
            logger.debug("SORT: single thread");
            try {
                return sortedSingleThread(sortBy);
            } finally {
                logger.debug("SORT: done single thread");
            }
//        }
//        return sortedMultiThreaded(sortBy);
    }

    private HitsMutable sortedMultiThreaded(HitProperty sortBy) {
        logger.debug("SORT: launch threads");

        // TODO: make sort and merge steps abortable (ThreadAborter). same for grouping.

        // We need to sort the hits from each segment separately (in parallel), then merge them.

        // Sort each group of segments in a separate thread.
        Map<String, CollationKey> collationKeyCache = new ConcurrentHashMap<>();
        Parallel<Map.Entry<LeafReaderContext, HitsMutable>, SegmentHits> parallel = new Parallel<>(index(),
                maxThreadsPerOperation);
        List<Future<List<SegmentHits>>> pendingResults = parallel.map(hitsPerSegment.entrySet(),
                entry -> entry.getValue().size(),
                entryList -> entryList.stream().map(entry -> {
                    // For each segment, sort the hits using the specified property.
                    Hits hits = entry.getValue().getStatic();
                    LeafReaderContext lrc = entry.getKey();
                    HitProperty sortByWithContext = sortBy.copyWith(PropContext.segmentHits(hits, lrc,
                            collationKeyCache));
                    Hits sorted = hits.sorted(sortByWithContext);
                    return new SegmentHits(lrc, sorted);
                }).toList());

        logger.debug("SORT: gather results");
        // Merge the results from all segments.
        MergePriorityQueue queue = sortGatherResults(hitsPerSegment.size(), sortBy, pendingResults, parallel);
        logger.debug("SORT: merge results");
        try {
            return sortMergeResults(queue);
        } finally {
            logger.debug("SORT: done");
        }
    }

    /**
     * Gather sorted segment hits and put them in the merge queue
     */
    private MergePriorityQueue sortGatherResults(int numberOfSegments, HitProperty sortBy,
            List<Future<List<SegmentHits>>> pendingResults,
            Parallel<Map.Entry<LeafReaderContext, HitsMutable>, SegmentHits> parallel) {
        MergePriorityQueue queue = new MergePriorityQueue(numberOfSegments);
        for (int i = 0; i < pendingResults.size(); i++) {
            try {
                for (SegmentHits segmentResults: parallel.nextResult()) {
                    queue.add(new SegmentInMerge(segmentResults, sortBy));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // preserve interrupted status
                parallel.cancelTasks(pendingResults);
                throw new InterruptedSearch(e);
            } catch (ExecutionException e) {
                throw new InterruptedSearch(e);
            }
        }
        return queue;
    }

    private HitsMutable sortMergeResults(MergePriorityQueue queue) {
        // Now merge the sorted hits from each segment.
        HitsMutable mergedHits = HitsMutable.create(field(), matchInfoDefs(),
                numHitsGlobalView, numHitsGlobalView, false);

        EphemeralHit hit = new EphemeralHit();
        while (!queue.top().done()) {
            SegmentInMerge segment = queue.top();
            segment.getHitAndAdvance(hit);
            hit.convertDocIdToGlobal(segment.docBase);
            mergedHits.add(hit);
            queue.updateTop(); // re-order the queue after taking a hit
        }
        return mergedHits;
    }

    /**
     * Sort the hits in a single thread.
     */
    private Hits sortedSingleThread(HitProperty sortBy) {
        size(); // Fetch all hits. Was already called, but just in case we reuse this method.
        HitsMutable mergedHits = HitsMutable.create(field(), matchInfoDefs(),
                numHitsGlobalView, numHitsGlobalView, false);
        for (Map.Entry<LeafReaderContext, HitsMutable> segmentHits: hitsPerSegment.entrySet()) {
            Hits hits = segmentHits.getValue().getStatic();
            mergedHits.addAllConvertDocBase(hits, segmentHits.getKey().docBase);
        }
        HitProperty sortByWithContext = sortBy.copyWith(PropContext.globalHits(mergedHits, new ConcurrentHashMap<>()));
        return mergedHits.sorted(sortByWithContext);
    }

    /**
     * Deal with new hits by adding stretches to the global view.
     */
    private class GlobalViewIntoSegmentHitsStrategy implements SpansReader.Strategy {

        /**
         * The segment we're looking at
         */
        private final LeafReaderContext lrc;

        /**
         * The list of hits in this segment to add new hits to
         */
        private final HitsMutable segmentHits;

        /**
         * Number of hits already added to the global view.
         * <p>
         * Therefore also the first hit index that's not yet part of the global view.
         */
        long numberAddedToGlobalView;

        public GlobalViewIntoSegmentHitsStrategy(LeafReaderContext lrc) {
            // We'll collect the segment hits here. It has to lock, because we'll be writing and reading from it.
            this.lrc = lrc;
            this.segmentHits = HitsMutable.create(field(), matchInfoDefs, -1, true, true);
            registerSegment(lrc, segmentHits);
            numberAddedToGlobalView = 0;
        }

        @Override
        public SpansReader.Phase onDocumentBoundary(HitsMutable results, long counted) {
            // Add new hits to the segment results.
            segmentHits.addAll(results);

            // Update stats and determine phase (fetching/counting/done)
            SpansReader.Phase phase = hitsStats.add(results.size(), counted);

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
            results.clear();

            return phase;
        }

        @Override
        public void onFinished(HitsMutable results, long counted) {
            // Add the final batch of hits to the segment results.
            segmentHits.addAll(results);

            // Update stats and determine phase (fetching/counting/done)
            SpansReader.Phase phase = hitsStats.add(results.size(), counted);

            addStretchIfLargeEnough(0);
        }

        /**
         * Add the latest stretch of hits we've found to the global view.
         *
         * @return
         */
        private void addStretchIfLargeEnough(long threshold) {
            if (segmentHits.size() - numberAddedToGlobalView > threshold) {
                addStretchFromSegment(lrc, segmentHits, numberAddedToGlobalView);
                numberAddedToGlobalView = segmentHits.size();
            }
        }
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

    /** Call optimize() and rewrite() on the source query, and create a weight for it.
     *
     * @param sourceQuery the source query to optimize and rewrite
     * @param fiMatchFactor override FI match threshold (debug use only, -1 means no override)
     * @return the weight for the optimized/rewritten query
     */
    protected BLSpanWeight rewriteAndCreateWeight(BLSpanQuery sourceQuery,
            long fiMatchFactor) {
        timings.start();

        // Override FI match threshold? (debug use only!)
        try {
            BLSpanQuery optimizedQuery;
            synchronized (ClauseCombinerNfa.class) {
                long oldFiMatchValue = ClauseCombinerNfa.getNfaThreshold();
                if (fiMatchFactor != -1) {
                    logger.debug("setting NFA threshold for this query to {}", fiMatchFactor);
                    ClauseCombinerNfa.setNfaThreshold(fiMatchFactor);
                }

                boolean traceOptimization = BlackLab.config().getLog().getTrace().isOptimization();
                if (traceOptimization)
                    logger.debug("Query before optimize()/rewrite(): {}", sourceQuery);

                optimizedQuery = sourceQuery.optimize(index().reader());
                if (traceOptimization)
                    logger.debug("Query after optimize(): {}", optimizedQuery);

                optimizedQuery = optimizedQuery.rewrite(index().reader());
                if (traceOptimization)
                    logger.debug("Query after rewrite(): {}", optimizedQuery);

                // Restore previous FI match threshold
                if (fiMatchFactor != -1) {
                    ClauseCombinerNfa.setNfaThreshold(oldFiMatchValue);
                }
            }
            timings.record("rewrite");

            // This call can take a long time
            BLSpanWeight weight = optimizedQuery.createWeight(index().searcher(),
                    ScoreMode.COMPLETE_NO_SCORES, 1.0f);
            timings.record("createWeight");
            return weight;
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    void setDone() {
        allSourceSpansFullyRead = true;
        hitsStats.setDone();
        docsStats.setDone();
    }

}
