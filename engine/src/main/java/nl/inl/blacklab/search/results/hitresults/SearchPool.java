package nl.inl.blacklab.search.results.hitresults;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** An ExecutorService that will allow at most maxThreads
 * to run on the shared thread pool at a time.
 *
 * Thread-safe (used by several HitPublisherSpans instances).
 * Doesn't need to be shut down, as it's just a wrapper around
 * an existing ExecutorService.
 */
public class SearchPool extends AbstractExecutorService {

    /** Thread pool to which we'll submit out tasks. */
    private final ExecutorService parent;

    /** Maximum threads we're allowed to have in the pool. */
    private final int maxThreads;

    /** Number of tasks currently in the pool */
    int tasksRunning = 0;

    /** Tasks waiting to be submitted to the thread pool. */
    List<Runnable> waitingTasks = new ArrayList<>();

    /** Are we in shutdown mode (i.e. no new tasks allowed) */
    private boolean shutdown = false;

    /** Are we terminated (i.e. shut down, all tasks done) */
    private boolean terminated = false;

    public SearchPool(ExecutorService parent, int maxThreads) {
        if (parent == null)
            throw new IllegalArgumentException("Parent executor service cannot be null");
        if (maxThreads < 1)
            throw new IllegalArgumentException("maxThreads must be at least 1");
        this.parent = parent;
        this.maxThreads = maxThreads;
    }

    @Override
    public synchronized void shutdown() {
        shutdown = true;
        manageTasks();
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        try {
            return new ArrayList<>(waitingTasks);
        } finally {
            shutdown = true;
            waitingTasks.clear();
        }
    }

    @Override
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return terminated;
    }

    @Override
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
            while (!terminated) {
                long timeLeft = endTime - System.currentTimeMillis();
                if (timeLeft <= 0)
                    return false;
                this.wait(timeLeft);
            }
            return true;
        } catch (InterruptedException e) {
            // Preserve interrupted status
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    @Override
    public synchronized void execute(Runnable command) {
        if (shutdown)
            throw new IllegalStateException("SearchPool is shut down");
        waitingTasks.add(command);
        manageTasks();
    }

    private synchronized void taskDone() {
        tasksRunning--;
        manageTasks();
    }

    /** If possbile, start task(s). If shutting down, see if we're terminated. */
    private synchronized void manageTasks() {
        // Start new tasks if possible
        while (!waitingTasks.isEmpty() && tasksRunning < maxThreads) {
            Runnable task = waitingTasks.remove(0);
            tasksRunning++;
            parent.execute(() -> {
                try {
                    task.run();
                } finally {
                    taskDone();
                }
            });
        }
        if (shutdown && !terminated) {
            // See if we're terminated
            if (tasksRunning == 0 && waitingTasks.isEmpty()) {
                terminated = true;
                this.notifyAll();
            }
        }
    }
}
