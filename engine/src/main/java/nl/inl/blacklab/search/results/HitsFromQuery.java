package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;

public class HitsFromQuery extends HitsFromQueryAbstract {

    /** Objects getting the actual hits from each index segment and adding them to the global results list. */
    protected final List<SpansReader> spansReaders = new ArrayList<>();

    protected HitsFromQuery(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings) {
        // NOTE: we explicitly construct HitsInternal so they're writeable
        super(queryInfo.optOverrideField(sourceQuery),
                HitsInternal.create(queryInfo.optOverrideField(sourceQuery).field(),
                        null, -1, true, true), searchSettings);
        BLSpanWeight weight = rewriteAndCreateWeight(queryInfo, sourceQuery, searchSettings.fiMatchFactor());

        for (LeafReaderContext leafReaderContext : queryInfo.index().reader().leaves()) {
            spansReaders.add(new SpansReader(
                weight,
                leafReaderContext,
                this.hitQueryContext,
                this.hitsInternalMutable,
                this.requestedHitsToProcess,
                this.requestedHitsToCount,
                    hitsStats,
                    docsStats
            ));
        }

        if (spansReaders.isEmpty())
            setDone();
    }

    @Override
    protected boolean ensureResultsRead(long number) {
        // clamp number to [current requested, number, max. requested], defaulting to max if number < 0
        final long clampedNumber = number < 0 ? maxHitsToCount : Math.min(number + FETCH_HITS_MIN, maxHitsToCount);

        if (allSourceSpansFullyRead || hitsInternalMutable.size() >= clampedNumber) {
            return hitsInternalMutable.size() >= number;
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
                if (allSourceSpansFullyRead || (hitsInternalMutable.size() >= clampedNumber)) {
                    return hitsInternalMutable.size() >= number;
                }
            }
            hasLock = true;
            
            // This is the blocking portion, start worker threads, then wait for them to finish.
            final ExecutorService executorService = getExecutorService();
            // Distribute the SpansReaders over the threads.
            // E.g. if we have 10 SpansReaders and 3 threads, we will have
            // SpansReader 0, 3, 6 and 9 in thread 1, etc.
            // This way, each thread will get a roughly equal number of SpansReaders to run.
            final AtomicLong i = new AtomicLong();
            pendingResults = spansReaders
                .stream()
                .collect(Collectors.groupingBy(sr -> i.getAndIncrement() % numThreads)) // subdivide the list, one sublist per thread to use (one list in case of single thread).
                .values()
                .stream()
                .map(list -> executorService.submit(() -> list.forEach(SpansReader::run))) // now submit one task per sublist
                .toList(); // gather the futures

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
        return hitsInternalMutable.size() >= number;
    }

}
