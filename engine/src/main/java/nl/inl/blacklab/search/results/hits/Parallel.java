package nl.inl.blacklab.search.results.hits;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.BlackLabIndex;

public class Parallel {

    private final int numThreads;

    private final ExecutorService executorService;

    public Parallel(BlackLabIndex index, int numThreads) {
        this.numThreads = numThreads;
        executorService = index.blackLab().searchExecutorService(numThreads);
    }

    public <I, O, H> O mapReduce(
            Collection<I> items,
            Function<I, Long> sizeGetter,
            Function<I, H> mapper,
            BiConsumer<O, H> reducer,
            O emptyResult) {
        return reduce(map(items, sizeGetter, mapper), reducer, emptyResult);
    }

    public <I, O> List<Future<List<O>>> map(
            Collection<I> items,
            Function<I, Long> sizeGetter,
            Function<I, O> mapper) {
        List<List<I>> threadInputs = HitsFromQuery.makeEqualGroups(items, sizeGetter, numThreads);
        List<Future<List<O>>> futures = new ArrayList<>();
        for (List<I> threadItems: threadInputs) {
            Future<List<O>> future = executorService.submit(() -> threadItems.stream().map(mapper).toList());
            futures.add(future);
        }
        return futures;
    }

    public <I> List<Future<?>> forEach(List<I> spansReaders,
            Function<I, Long> sizeGetter,
            Consumer<I> task) {
        List<List<I>> threadInputs = HitsFromQuery.makeEqualGroups(spansReaders, sizeGetter, numThreads);
        List<? extends Future<?>> futures = threadInputs.stream()
                .map(threadItems -> {
                    Future<?> future = executorService.submit(() -> {
                        for (I item: threadItems) {
                            task.accept(item);
                        }
                    });
                    return future;
                })
                .toList();
        return (List<Future<?>>) futures;
    }

    public <I, O> O reduce(
            List<Future<List<I>>> futures,
            BiConsumer<O, I> reducer,
            O emptyResult) {
        // TODO: make parallel:
        //   wait for enough futures to complete, then reduce them in a separate thread
        //   finally, combine the results in the main thread
        try {
            for (Future<List<I>> future: futures) {
                future.get().forEach(partialResult -> reducer.accept(emptyResult, partialResult));
            }
            return emptyResult;
        } catch (InterruptedException e) {
            // We were interrupted while waiting for workers to finish.
            // If we were the thread that created the workers, cancel them. (this isn't always the case, we may have been interrupted during self-polling phase)
            // For the TermsReaders that aren't done yet, the next time this function is called we'll just create new Runnables/Futures of them.
            Thread.currentThread().interrupt(); // preserve interrupted status
            cancelTasks(futures);
            throw new InterruptedSearch(e);
        } catch (ExecutionException e) {
            throw new InterruptedSearch(e);
        }
    }

    public static void cancelTasks(List<?> futures) {
        if (futures != null) {
            for (Object p: futures)
                if (p instanceof Future<?> f)
                    f.cancel(true);
        }
    }
}
