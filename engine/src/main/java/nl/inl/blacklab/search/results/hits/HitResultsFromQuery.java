package nl.inl.blacklab.search.results.hits;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;

public class HitResultsFromQuery extends HitResultsFromQueryAbstract {

    public static HitResultsFromQuery get(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        return new HitResultsFromQueryKeepSegments(queryInfo, sourceQuery, searchSettings);
    }

    /** Objects getting the actual hits from each index segment and adding them to the global results list. */
    protected final List<SpansReader> spansReaders = new ArrayList<>();

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
    public static <T> List<List<T>> makeEqualGroups(List<T> items, Function<T, Long> sizeGetter, int numberOfGroups) {
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

    /** Number of hits in the global view. Needed because we don't want to call hitsInternalMutable.size() from
     *  HitsFromQueryKeepSegments, because that class doesn't use it. (REFACTOR THIS!) */
    protected long globalHitsSoFar() {
        return hitsInternalMutable.size();
    }

    protected HitResultsFromQuery(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        // NOTE: we explicitly construct HitsInternal so they're writeable
        super(queryInfo.optOverrideField(sourceQuery),
                HitsInternalMutable.create(queryInfo.optOverrideField(sourceQuery).field(), null, -1, true, true), searchSettings);
        BLSpanWeight weight = rewriteAndCreateWeight(queryInfo, sourceQuery, searchSettings.fiMatchFactor());

        for (LeafReaderContext leafReaderContext: queryInfo.index().reader().leaves()) {
            spansReaders.add(new SpansReader(
                weight,
                leafReaderContext,
                this.hitQueryContext,
                getSpansReaderStrategy(leafReaderContext),
                this.requestedHitsToProcess,
                this.requestedHitsToCount,
                    hitsStats,
                    docsStats
            ));
        }

        if (spansReaders.isEmpty())
            setDone();
    }

    protected SpansReader.Strategy getSpansReaderStrategy(LeafReaderContext lrc) {
        return new SpansReaderStrategyAddToGlobal(lrc);
    }

    @Override
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
        List<? extends Future<?>> pendingResults = null;
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
            final ExecutorService executorService = getExecutorService();

            // Distribute the SpansReaders over the threads.
            // Make sure the number of documents per segment is roughly equal for each thread.
            Function<SpansReader, Long> sizeGetter = spansReader ->
                    spansReader.leafReaderContext == null ? 0 : (long) spansReader.leafReaderContext.reader().maxDoc();
            pendingResults = makeEqualGroups(spansReaders, sizeGetter, numThreads).stream()
                .map(list -> executorService.submit(() -> list.forEach(SpansReader::run)))
                .toList();

            // Distribute the SpansReaders over the threads.
            // E.g. if we have 10 SpansReaders and 3 threads, we will have
            // SpansReader 0, 3, 6 and 9 in thread 1, etc.
            // This way, each thread will get a roughly equal number of SpansReaders to run.
//            final AtomicLong i = new AtomicLong();
//            pendingResults = spansReaders
//                .stream()
//                .collect(Collectors.groupingBy(sr -> i.getAndIncrement() % numThreads)) // subdivide the list, one sublist per thread to use (one list in case of single thread).
//                .values()
//                .stream()
//                .map(list -> executorService.submit(() -> list.forEach(SpansReader::run))) // now submit one task per sublist
//                .toList(); // gather the futures

            // Wait for workers to complete.
            // This will throw InterrupedException if this (HitsFromQuery) thread is interruped while waiting.
            // NOTE: the worker will not automatically abort, so we should also interrupt our workers should that happen.
            // The workers themselves won't ever throw InterruptedException, it would be wrapped in ExecutionException.
            // (Besides, we're the only thread that can call interrupt() on our worker anyway, and we don't ever do that.
            //  Technically, it could happen if the Executor were to shut down, but it would still result in an ExecutionException anyway.)
            for (Future<?> p : pendingResults) 
                p.get();
        } catch (InterruptedException e) {
            // We were interrupted while waiting for workers to finish.
            // If we were the thread that created the workers, cancel them. (this isn't always the case, we may have been interrupted during self-polling phase)
            // For the TermsReaders that aren't done yet, the next time this function is called we'll just create new Runnables/Futures of them.
            Thread.currentThread().interrupt(); // preserve interrupted status
            if (pendingResults != null) {
                for (Future<?> p : pendingResults) 
                    p.cancel(true);
            }
            throw new InterruptedSearch(e);
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

    /** Adds hits to global list regularly. */
    private class SpansReaderStrategyAddToGlobal implements SpansReader.Strategy {

        private final LeafReaderContext lrc;

        SpansReaderStrategyAddToGlobal(LeafReaderContext lrc) {
            this.lrc = lrc;
        }

        /**
         * How many hits should we collect (at least) before we add them to the global results?
         */
        private static final int ADD_HITS_TO_GLOBAL_THRESHOLD = 100;

        @Override
        public void onDocumentBoundary(HitsInternalMutable results) {
            if (results.size() >= ADD_HITS_TO_GLOBAL_THRESHOLD) {
                // We've built up a batch of hits. Add them to the global results.
                // We do this only once per doc, so hits from the same doc remain contiguous in the master list.
                addAll(results);
                results.clear();
            }
        }

        private void addAll(HitsInternalMutable results) {
            for (EphemeralHit h: results) {
                convertToGlobal(h, lrc.docBase);
                hitsInternalMutable.add(h);
            }
        }

        @Override
        public void onFinished(HitsInternalMutable results) {
            if (!results.isEmpty()) {
                // Add the final batch of hits to the global results.
                addAll(results);
            }
        }
    }

    /**
     * Convert a hit to a global hit by adding the document base.
     *
     * Each segment has a document base, which is the lowest document ID in that segment.
     * Adding the document base converts the document ID of the hit to a global document ID.
     *
     * @param hit the hit to convert
     * @param docBase the document base to add
     */
    static void convertToGlobal(EphemeralHit hit, int docBase) {
        hit.doc_ += docBase;
    }
}
