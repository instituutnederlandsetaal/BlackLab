package nl.inl.blacklab.search.results.hits.fetch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hits.Parallel;

/**
 * (Lazily) fetches hits from a source, in parallel if possible.
 */
public abstract class HitFetcherAbstract implements HitFetcher {

    /**
     * Testing reveals this to be a good number of threads for fetching hits in parallel.
     * More is not useful, and we can afford to use 4 threads as fetching hits is a very fast operation
     * compared to sort/group.
     */
    public static final int IDEAL_NUM_THREADS_FETCHING = 4;

    /**
     * If another thread is busy fetching hits and we're monitoring it, how often should we check?
     */
    private static final int HIT_POLLING_TIME_MS = 50;

    /**
     * Minimum number of hits to fetch in an ensureHitsRead() block.
     * <p>
     * This prevents locking again and again for a single hit when iterating.
     */
    private static final int FETCH_HITS_MIN = 20;

    /**
     * Max. number of threads to use for fetch, sort, group.
     */
    private final int maxThreadsPerOperation;

    /**
     * Configured upper limit of requestedHitsToProcess, to which it will always be clamped.
     */
    private final long maxHitsToProcess;

    /**
     * Configured upper limit of requestedHitsToCount, to which it will always be clamped.
     */
    private final long maxHitsToCount;

    /**
     * Used to make sure that only 1 thread can be fetching hits at a time.
     */
    private final Lock ensureHitsReadLock = new ReentrantLock();

    final HitQueryContext hitQueryContext;

    /**
     * Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1
     */
    final AtomicLong requestedHitsToProcess = new AtomicLong();

    /**
     * Should be normalized and clamped to configured maximum, i.e. always max >= requested >= 1
     */
    final AtomicLong requestedHitsToCount = new AtomicLong();

    private final SearchSettings searchSettings;

    HitCollector hitCollector;

    final BlackLabIndex index;

    /**
     * If true, all hits have been processed.
     */
    boolean done = false;

    /**
     * Objects getting the actual hits from each index segment and adding them to the global results list.
     */
    final List<HitFetcherSegment> segmentReaders = new ArrayList<>();

    public HitFetcherAbstract(AnnotatedField field, SearchSettings searchSettings) {
        this.index = field.index();
        this.searchSettings = searchSettings;
        hitQueryContext = new HitQueryContext(index, null, field); // each spans will get a copy
        maxThreadsPerOperation = Math.max(index.blackLab().maxThreadsPerSearch(), 1);
        maxHitsToProcess = searchSettings == null ? Long.MAX_VALUE : searchSettings.maxHitsToProcess();
        maxHitsToCount = searchSettings == null ? Long.MAX_VALUE : searchSettings.maxHitsToCount();
    }

    @Override
    public boolean ensureResultsRead(long number) {
        // clamp number to [current requested, number, max. requested], defaulting to max if number < 0
        final long clampedNumber = number < 0 ? maxHitsToCount : Math.min(number + FETCH_HITS_MIN, maxHitsToCount);

        if (isDone() || hitCollector.globalHitsSoFar() >= clampedNumber) {
            return hitCollector.globalHitsSoFar() >= number;
        }

        // NOTE: we first update to process, then to count. If we do it the other way around, and spansReaders
        //       are running, they might check in between the two statements and conclude that they don't need to save
        //       hits anymore, only count them.
        requestedHitsToProcess.getAndUpdate(
                c -> Math.max(Math.min(clampedNumber, maxHitsToProcess), c)); // update process
        requestedHitsToCount.getAndUpdate(c -> Math.max(clampedNumber, c)); // update count

        boolean hasLock = false;
        int numThreads = Math.min(IDEAL_NUM_THREADS_FETCHING, maxThreadsPerOperation);
        Parallel<HitFetcherSegment, Void> parallel = new Parallel<>(index, numThreads);
        try {
            while (!ensureHitsReadLock.tryLock(HIT_POLLING_TIME_MS, TimeUnit.MILLISECONDS)) {
                /*
                 * Another thread is already working on hits, we don't want to straight up block until it's done,
                 * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction.
                 * So instead poll our own state, then if we're still missing results after that just count them ourselves
                 */
                if (done || (hitCollector.globalHitsSoFar() >= clampedNumber)) {
                    return hitCollector.globalHitsSoFar() >= number;
                }
            }
            hasLock = true;

            // This is the blocking portion, start worker threads, then wait for them to finish.

            // Distribute the SpansReaders over the threads.
            // Make sure the number of documents per segment is roughly equal for each thread.
            Function<HitFetcherSegment, Long> sizeGetter = spansReader ->
                    spansReader.getLeafReaderContext() == null ? 0 : (long) spansReader.getLeafReaderContext().reader().maxDoc();
            List<Future<List<Void>>> pendingResults = parallel.forEach(segmentReaders, sizeGetter,
                    l -> l.forEach(HitFetcherSegment::run));

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
                segmentReaders.removeIf(HitFetcherSegment::isDone);
                if (segmentReaders.isEmpty())
                    hitCollector.setDone(); // all spans have been read, so we're done
                ensureHitsReadLock.unlock();
            }
        }
        return hitCollector.globalHitsSoFar() >= number;
    }

    public boolean isDone() {
        return done;
    }

    @Override
    public HitQueryContext getHitQueryContext() {
        return hitQueryContext;
    }

    @Override
    public AnnotatedField field() {
        return hitQueryContext.getField();
    }

    @Override
    public long getMaxHitsToProcess() {
        return maxHitsToProcess;
    }

    @Override
    public long getMaxHitsToCount() {
        return maxHitsToCount;
    }
}
