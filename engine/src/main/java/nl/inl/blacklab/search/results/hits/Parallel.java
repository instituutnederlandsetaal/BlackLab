package nl.inl.blacklab.search.results.hits;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.BlackLabIndex;

public class Parallel<I, O> {

    private final int numThreads;

    private final ExecutorCompletionService<List<O>> executorCompletionService;

    private int tasksStarted = 0;

    public Parallel(BlackLabIndex index, int numThreads) {
        this.numThreads = numThreads;
        executorCompletionService = new ExecutorCompletionService<>(index.blackLab().searchExecutorService(numThreads));
    }

    public O mapReduce(
            Collection<I> items,
            Function<I, Long> sizeGetter,
            Function<List<I>, List<O>> mapper,
            BiConsumer<O, O> reducer,
            Supplier<O> outputTypeSupplier) {
        return reduce(map(items, sizeGetter, mapper), reducer, outputTypeSupplier);
    }

    public List<Future<List<O>>> map(
            Collection<I> items,
            Function<I, Long> sizeGetter,
            Function<List<I>, List<O>> mapper) {
        List<List<I>> threadInputs = HitsFromQuery.makeEqualGroups(items, sizeGetter, numThreads);
        List<Future<List<O>>> futures = new ArrayList<>();
        for (List<I> threadItems: threadInputs) {
            Future<List<O>> future = executorCompletionService.submit(() -> mapper.apply(threadItems));
            tasksStarted++;
            futures.add(future);
        }
        return futures;
    }

    public List<Future<List<O>>> forEach(List<I> spansReaders,
            Function<I, Long> sizeGetter,
            Consumer<List<I>> task) {
        List<List<I>> threadInputs = HitsFromQuery.makeEqualGroups(spansReaders, sizeGetter, numThreads);
        return threadInputs.stream()
                .map(threadItems -> {
                    Future<List<O>> f = executorCompletionService.submit(() -> {
                        task.accept(threadItems);
                        return Collections.emptyList();
                    });
                    tasksStarted++;
                    return f;
                })
                .toList();
    }

    public O reduce(
            List<Future<List<O>>> futures,
            BiConsumer<O, O> reducer,
            Supplier<O> outputTypeSupplier) {
        // TODO: make parallel:
        //   wait for enough futures to complete, then reduce them in a separate thread
        //   finally, combine the results in the main thread
        try {
            O acc = outputTypeSupplier.get();
            for (int i = 0; i < futures.size(); i++) {
                List<O> partialResult = executorCompletionService.take().get();
                tasksStarted--;
                partialResult.forEach(partial -> reducer.accept(acc, partial));
            }
            return acc;
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

    public void cancelTasks(List<Future<List<O>>> futures) {
        if (futures != null) {
            for (Future<List<O>> f: futures)
                f.cancel(true);
        }
    }

    public List<O> nextResult() throws InterruptedException, ExecutionException {
        List<O> result = executorCompletionService.take().get();
        tasksStarted--;
        return result;
    }

    public List<List<O>> waitForAll(List<Future<List<O>>> pending) throws InterruptedException, ExecutionException {
        try {
            List<List<O>> results = new ArrayList<>();
            while (tasksStarted > 0) {
                results.add(nextResult());
            }
            return results;
        } catch (InterruptedException e) {
            // We were interrupted while waiting for workers to finish.
            // If we were the thread that created the workers, cancel them. (this isn't always the case, we may have been interrupted during self-polling phase)
            // For the TermsReaders that aren't done yet, the next time this function is called we'll just create new Runnables/Futures of them.
            Thread.currentThread().interrupt(); // preserve interrupted status
            cancelTasks(pending);
            throw new InterruptedSearch(e);
        }
    }
}
