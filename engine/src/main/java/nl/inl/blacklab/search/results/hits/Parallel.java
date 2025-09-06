package nl.inl.blacklab.search.results.hits;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
        List<List<I>> threadInputs = makeEqualGroups(items, sizeGetter, numThreads);
        List<Future<List<O>>> futures = new ArrayList<>();
        for (List<I> threadItems: threadInputs) {
            Future<List<O>> future = executorCompletionService.submit(() -> mapper.apply(threadItems));
            tasksStarted++;
            futures.add(future);
        }
        return futures;
    }

    public List<Future<List<O>>> forEach(List<I> items,
            Function<I, Long> sizeGetter,
            Consumer<List<I>> task) {
        List<List<I>> threadInputs = makeEqualGroups(items, sizeGetter, numThreads);
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
