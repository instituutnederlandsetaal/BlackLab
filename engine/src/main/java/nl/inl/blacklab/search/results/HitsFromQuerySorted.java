package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;

public class HitsFromQuerySorted extends HitsFromQueryAbstract {

    /** Our query weight */
    private final BLSpanWeight weight;

    /** What to sort our hits by */
    private final HitProperty sortBy;

    /** Sorted hits from each segment to merge */
    private List<HitsSimple> segmentHits;

    protected HitsFromQuerySorted(QueryInfo queryInfo, BLSpanQuery sourceQuery, SearchSettings searchSettings, HitProperty sortBy) {
        // NOTE: we explicitly construct HitsInternal so they're writeable
        super(queryInfo.optOverrideField(sourceQuery), /*@@@@WRONG*/(HitsInternalMutable)HitsInternal.empty(queryInfo.optOverrideField(sourceQuery).field(),
                null), searchSettings);
        this.sortBy = sortBy;
        weight = rewriteAndCreateWeight(queryInfo, sourceQuery, searchSettings.fiMatchFactor());
    }

    @Override
    protected boolean ensureResultsRead(long number) {
        // Make sure the hits from all segments are gathered and sorted, so we can merge them.
        if (segmentHits == null && number > 0) {
            segmentHits = gatherAndSortForAllSegments(sortBy);
        }

        // TODO: we need to merge hits from the segments here.
        //       how do we avoid keeping a lot of memory occupied?

        return resultsStats().processedSoFar() >= number;
    }

    private List<HitsSimple> gatherAndSortForAllSegments(HitProperty sortBy) {
        List<Future<List<HitsSimple>>> pendingResults = null;
        List<HitsSimple> segmentHits = new ArrayList<>();
        try {

            // This is the blocking portion, start worker threads, then wait for them to finish.
            final ExecutorService executorService = getExecutorService();
            // Distribute the SpansReaders over the threads.
            // E.g. if we have 10 SpansReaders and 3 threads, we will have
            // SpansReader 0, 3, 6 and 9 in thread 1, etc.
            // This way, each thread will get a roughly equal number of SpansReaders to run.
            final AtomicLong i = new AtomicLong();
            pendingResults = queryInfo().index().reader().leaves()
                    .stream()
                    .collect(Collectors.groupingBy(sr -> i.getAndIncrement() % numThreads)) // subdivide the list, one sublist per thread to use (one list in case of single thread).
                    .values()
                    .stream()
                    .map(list -> executorService.submit(() -> list.stream().map((lrc) -> {
                        // Gather and sort all hits for this segment.
                        HitsInternalMutable hits = HitsInternal.gatherAll(weight, lrc, hitQueryContext);
                        return (HitsSimple)hits.sorted(sortBy.copyWith(hits, lrc));
                    }).toList())) // now submit one task per sublist
                    .toList(); // gather the futures
            // Wait for workers to complete.
            // This will throw InterrupedException if this (HitsFromQuerySorted) thread is interruped while waiting.
            // NOTE: the worker will not automatically abort, so we should also interrupt our workers should that happen.
            // The workers themselves won't ever throw InterruptedException, it would be wrapped in ExecutionException.
            // (Besides, we're the only thread that can call interrupt() on our worker anyway, and we don't ever do that.
            //  Technically, it could happen if the Executor were to shut down, but it would still result in an ExecutionException anyway.)
            for (Future<List<HitsSimple>> p: pendingResults)
                segmentHits.addAll(p.get());
            return segmentHits;
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
        }
    }

}
