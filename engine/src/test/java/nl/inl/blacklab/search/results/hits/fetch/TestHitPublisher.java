package nl.inl.blacklab.search.results.hits.fetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.jspecify.annotations.NonNull;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockAnnotatedField;
import nl.inl.blacklab.mocks.MockSpans;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsAbstract;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;

public class TestHitPublisher {
    /** Queues submitted work so tests can control exactly when a publisher worker runs. */
    private static class ManualExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override public synchronized void execute(Runnable command) { tasks.add(command); }

        /** Runs the next queued task on the calling test thread. */
        void runNext() {
            Runnable task;
            synchronized (this) {
                task = tasks.remove();
            }
            task.run();
        }
        synchronized int pendingTasks() { return tasks.size(); }
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    /** Captures subscriber callbacks and the retained-hit view
     * for concise protocol assertions. Requests all hits. */
    private static class RecordingSubscriber implements HitSubscriber {
        Hits retained;

        /** Number of hits processed via hits() method */
        long hitsProcessed;

        /** Number of hits counted via counted() method */
        long hitsCounted;

        /** Number of docs counted */
        int docsCounted;

        /** Number of hits published, as reported to flush() */
        long flushed;

        /** How many times done was called */
        int done;

        /** Error if error method was called, null otherwise */
        Throwable error;

        /** Sizes of the retained hits at each call to hits() */
        final List<Long> retainedSizes = new ArrayList<>();

        /** Events recorded in order of occurrence, for concise protocol assertions */
        final List<String> events = new ArrayList<>();

        @Override
        public void start(LeafReaderContext lrc, Hits results) {
            retained = results; events.add("start");
        }

        @Override
        public boolean needsMoreHits() {
            return true;
        }

        @Override
        public void counted(long hits, int docs) {
            hitsCounted += hits;
            docsCounted += docs;
            events.add("counted:" + hits + "/" + docs);
        }

        @Override
        public void hits(LeafReaderContext lrc, Hits batch, long start, long end, int docs, long offset) {
            hitsProcessed += end - start;
            retainedSizes.add(retained.size());
            events.add("hits:" + start + "-" + end + "@" + offset);
        }

        @Override
        public void flush(LeafReaderContext lrc, long published) {
            flushed = published;
            events.add("flush:" + published);
        }

        @Override
        public void done(LeafReaderContext lrc) {
            done++;
            events.add("done");
        }

        @Override
        public void error(LeafReaderContext lrc, Throwable exception) {
            error = exception;
            events.add("error");
        }
    }

    private static final MockSpans TEST_HITS = new MockSpans(new int[] { 0, 0, 1 }, new int[] { 0, 1, 0 }, new int[] { 1, 2, 1 });

    /** Builds a publisher that will produce a small list of specific spans. */
    private static HitPublisherSpans publisher(ExecutorService executor) throws IOException {
        return publisher(executor, TEST_HITS);
    }

    /** Wraps test spans in the mocked weight required by the production publisher. */
    private static HitPublisherSpans publisher(ExecutorService executor, MockSpans spans) throws IOException {
        return publisher(executor, getBlSpanWeight(spans));
    }

    /** Uses fresh statistics objects for tests that do not inspect statistics directly. */
    private static HitPublisherSpans publisher(ExecutorService executor, BLSpanWeight weight) {
        return publisher(executor, weight, new ResultsStatsPassive(), new ResultsStatsPassive());
    }

    /** Creates a spans publisher with configurable statistics and hit-retention behavior. */
    private static HitPublisherSpans publisher(ExecutorService executor, BLSpanWeight weight,
            ResultsStatsPassive hitsStats, ResultsStatsPassive docsStats) {
        LeafReaderContext lrc = mock(LeafReaderContext.class);
        when(lrc.reader()).thenReturn(mock(LeafReader.class));
        MockAnnotatedField field = new MockAnnotatedField();
        return new HitPublisherSpans(lrc, weight, new HitQueryContext(field.index(), null, field), executor,
                hitsStats, docsStats);
    }

    private static @NonNull MockSpans getMockSpans(int numberOfHits) {
        int[] docs = java.util.stream.IntStream.range(0, numberOfHits).toArray();
        int[] starts = new int[numberOfHits]; // all 0
        int[] ends = java.util.stream.IntStream.range(0, numberOfHits).map(i -> 1).toArray();
        return new MockSpans(docs, starts, ends);
    }

    private static @NonNull BLSpanWeight getBlSpanWeight(int numberOfHits) throws IOException {
        MockSpans mockSpans = getMockSpans(numberOfHits);
        return getBlSpanWeight(mockSpans);
    }

    private static @NonNull BLSpanWeight getBlSpanWeight(MockSpans mockSpans) throws IOException {
        BLSpanWeight weight = mock(BLSpanWeight.class);
        when(weight.getSpans(any(), any())).thenReturn(mockSpans);
        return weight;
    }

    // Count-only batches, including a partially published final batch, must be replayed to subscribers.
    @Test
    public void countOnlyBatchesAndFinalRemainderReachEarlyAndLateSubscribers() throws IOException {
        for (int numberOfHits: List.of(3, 102)) {
            testCountOnlyBatchesAndFinalRemainderReachEarlyAndLateSubscribers(numberOfHits);
        }
    }

    private static void testCountOnlyBatchesAndFinalRemainderReachEarlyAndLateSubscribers(int numberOfHits) throws IOException {
        ManualExecutor executor = new ManualExecutor();

        // Create simple spans with numberOfHits hits.
        // All hits are 0-1. The first hit is in doc 0, the second in doc 1, etc.
        BLSpanWeight weight = getBlSpanWeight(numberOfHits);

        // Set up a publisher that doesn't process, only counts hits
        ResultsStatsPassive hitsStats = new ResultsStatsPassive(0, Long.MAX_VALUE);
        ResultsStatsPassive docsStats = new ResultsStatsPassive();
        HitPublisherSpans publisher = publisher(executor, weight, hitsStats, docsStats);

        // Early subscriber wants up to 101 hits
        final int EARLY_SUBSCRIBER_LIMIT = 101;
        RecordingSubscriber early = new RecordingSubscriber() {
            @Override public boolean needsMoreHits() { return hitsCounted < EARLY_SUBSCRIBER_LIMIT; }
        };
        publisher.subscribe(early);

        // subscribe() calls activate() which adds a task to the executor. Run it now.
        // (it should count up to min(numberOfHits, 101) and then pause)
        executor.runNext();
        assertEquals(Math.min(numberOfHits, EARLY_SUBSCRIBER_LIMIT), early.hitsCounted);

        // Add another subscriber that requests all hits. Execute the worker.
        RecordingSubscriber late = new RecordingSubscriber();
        publisher.subscribe(late);
        while (executor.pendingTasks() > 0)
            executor.runNext();

        // Verify that both subscribers received the same final counts and that the publisher's stats match.
        for (RecordingSubscriber subscriber: List.of(early, late)) {
            assertNull(subscriber.error);
            assertEquals(numberOfHits, subscriber.hitsCounted);
            assertEquals(numberOfHits, subscriber.docsCounted);
            assertEquals(0, subscriber.hitsProcessed);
            assertEquals(1, subscriber.done);
        }
        assertEquals(numberOfHits, hitsStats.countedSoFar());
        assertEquals(numberOfHits, docsStats.countedSoFar());
    }

    // Subscribers joining after completion still receive the terminal callback and final flush state.
    @Test
    public void lateSubscribersReceiveTheTerminalNotification() {
        HitPublisherOutput output = new HitPublisherOutput(new Hits.HitsContext(new MockAnnotatedField()));
        output.flush();
        output.complete();
        RecordingSubscriber late = new RecordingSubscriber();
        output.subscribe(late);
        assertEquals(1, late.done);
        assertEquals(0, late.flushed);
        assertEquals(List.of("start", "flush:0", "done"), late.events);
    }

    // Reaching the processing limit publishes buffered hits before reporting the unprocessed remainder.
    @Test
    public void processingLimitPublishesStoredBatchBeforeCountOnlyRemainder() throws IOException {
        final int NUMBER_OF_HITS_AND_DOCS = 102;
        BLSpanWeight weight = getBlSpanWeight(NUMBER_OF_HITS_AND_DOCS);

        // Set up a publisher that processes at least one hit (it will actually be more because of batching)
        // but counts all of them, so we can verify the final counts.
        final int MAX_HITS_TO_PROCESS = 1;
        ResultsStatsPassive hitsStats = new ResultsStatsPassive(MAX_HITS_TO_PROCESS, Long.MAX_VALUE);
        ResultsStatsPassive docsStats = new ResultsStatsPassive();
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor, weight, hitsStats, docsStats);

        // Subscriber wants all hits
        RecordingSubscriber subscriber = new RecordingSubscriber();
        publisher.subscribe(subscriber);
        executor.runNext();

        // Check that the subscriber received at least the expected number of processed hits
        // and the final counts match the publisher's stats.
        assertTrue(subscriber.hitsProcessed >= MAX_HITS_TO_PROCESS);
        assertEquals(NUMBER_OF_HITS_AND_DOCS - subscriber.hitsProcessed, subscriber.hitsCounted);
        assertEquals(NUMBER_OF_HITS_AND_DOCS, hitsStats.countedSoFar());
        assertTrue(hitsStats.processedSoFar() >= MAX_HITS_TO_PROCESS);
        assertEquals(NUMBER_OF_HITS_AND_DOCS, docsStats.countedSoFar());
        assertTrue(docsStats.processedSoFar() >= MAX_HITS_TO_PROCESS);
        assertEquals(1, subscriber.done);
    }

    // A spans failure wakes static-result waiters, reaches current and late subscribers, and is terminal.
    @Test(timeout = 5000)
    public void failedSpansReleaseStaticWaitersAndNotifyLateSubscribers() throws IOException {
        // Set up a failing spans publisher
        RuntimeException failure = new IllegalStateException("spans failed");
        BLSpanWeight weight = mock(BLSpanWeight.class);
        when(weight.getSpans(any(), any())).thenThrow(failure);
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor, weight);

        // Subscriber should be notified of error
        RecordingSubscriber early = new RecordingSubscriber();
        publisher.subscribe(early);
        executor.runNext();
        assertSame(failure, early.error);
        assertEquals(0, early.done);

        // Trying to get static hits from publisher should throw the same failure
        assertSame(failure, assertThrows(RuntimeException.class, publisher::getStatic));

        // Late subscriber should also be notified of error
        RecordingSubscriber late = new RecordingSubscriber();
        publisher.subscribe(late);
        assertSame(failure, late.error);
        assertEquals(0, late.done);

        // Repeated activation should not queue any more work since the publisher is terminal
        assertEquals(0, executor.pendingTasks());
        for (int i = 0; i < 10; i++)
            publisher.activate();
        assertEquals(0, executor.pendingTasks());
    }

    // Filter publishers preserve upstream failures, including wrapped completion failures, for all waiters.
    @Test(timeout = 5000)
    public void failedFiltersReleaseStaticWaitersAndNotifyLateSubscribers() {
        List<RuntimeException> failures = List.of(new IllegalStateException("upstream failed"),
                new CompletionException(new IllegalStateException("wrapped failure")));
        for (RuntimeException failure: failures) {
            // Mock a publisher that will fail when subscribed to, and wrap it in a filter.
            HitPublisher source = mock(HitPublisher.class);
            when(source.context()).thenReturn(new Hits.HitsContext(new MockAnnotatedField()));
            HitPublisherFilter publisher = new HitPublisherFilter(source, mock(HitFilter.class));

            // Verify that the HitPublisherFilter called source.subscribe(...) exactly once,
            // and capture the subscriber that was passed to it. This allows the test to simulate an error below.
            var captor = org.mockito.ArgumentCaptor.forClass(HitSubscriber.class);
            org.mockito.Mockito.verify(source).subscribe(captor.capture());

            // Create subscriber that asks for all hits, and subscribe it to the filter publisher.
            RecordingSubscriber early = new RecordingSubscriber();
            publisher.subscribe(early);

            // Using the captured subscriber from the source, we can simulate an error by calling its error method
            // directly, simulating the source reporting a failure. The test checks that the filter forwards it to
            // the early subscriber and preserves it for static and late subscribers.
            captor.getValue().error(null, failure); // simulate error by calling filter subscriber directly

            // Verify that the early subscriber received the error
            assertSame(failure, early.error);
            assertEquals(0, early.done);

            // Verify that publisher.getStatic() throws the same failure.
            assertSame(failure, assertThrows(RuntimeException.class,
                    publisher::getStatic));

            // Verify that a late subscriber also receives the same error.
            RecordingSubscriber late = new RecordingSubscriber();
            publisher.subscribe(late);
            assertSame(failure, late.error);
            assertEquals(0, late.done);
        }
    }

    // performPerPublisher must not accidentally swallow assertion failures reported through the publisher protocol.
    @Test
    public void performPerPublisherPropagatesAssertionErrors() {
        // Set up a mock publisher that, when its subscribe() method is called,
        // will call error() on the subscriber with an AssertionError.
        HitPublisher publisher = mock(HitPublisher.class);
        AssertionError failure = new AssertionError("query assertion failed");
        org.mockito.Mockito.doAnswer(call -> {
            HitSubscriber subscriber = call.getArgument(0);
            subscriber.error(null, failure);
            return null;
        }).when(publisher).subscribe(any());

        // Assert that performPerPublisher propagates the AssertionError as the cause of a RuntimeException.
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> HitsAbstract.performPerPublisher(List.of(publisher), RecordingSubscriber::new, false));
        assertSame(failure, exception.getCause());
    }

    // performPerPublisher must complete if prefetchAll is set to true.
    @Test
    public void performPerPublisherCompletesForStaticPublishers() {
        {
            // Empty hits object
            Hits retained = Hits.empty(new Hits.HitsContext(new MockAnnotatedField()));

            // Set up a mock publisher that returns a static empty Hits object when getStatic() is called.
            HitPublisher staticSource = mock(HitPublisher.class);
            when(staticSource.getStatic()).thenReturn(retained);

            // Set up a subscriber that prefetches all (0) hits, meaning it will call getStatic().
            RecordingSubscriber staticSubscriber = new RecordingSubscriber();
            HitsAbstract.performPerPublisher(List.of(staticSource), () -> staticSubscriber, true);
            assertEquals(1, staticSubscriber.done);
        }
    }

    // performPerPublisher must complete for asynchronous streaming inputs.
    @Test
    public void performPerPublisherCompletesForAsynchronousStreamingPublishers() {
        // Empty hits object
        Hits retained = Hits.empty(new Hits.HitsContext(new MockAnnotatedField()));

        // Set up a mock publisher that simulates asynchronous streaming by calling start(), flush(), and done()
        // on the subscriber in a separate thread.
        RecordingSubscriber streamingSubscriber = new RecordingSubscriber();
        HitPublisher streamingSource = mock(HitPublisher.class);
        // When subscribe() is called, start a new thread that calls start(), flush(), and done() on the subscriber.
        org.mockito.Mockito.doAnswer(call -> {
            HitSubscriber subscriber = call.getArgument(0);
            Thread callbackThread = new Thread(() -> {
                subscriber.start(null, retained);
                subscriber.flush(null, 0);
                subscriber.done(null);
            });
            callbackThread.setDaemon(true);
            callbackThread.start();
            return null;
        }).when(streamingSource).subscribe(any());

        // Run performPerPublisher with the streaming source and subscriber, and verify that the subscriber received the expected callbacks.
        // (note that we can be sure the thread has finished because performPerPublisher waits for completion, i.e.
        //  the done() callback has to have been called for all subscribers)
        HitsAbstract.performPerPublisher(List.of(streamingSource), () -> streamingSubscriber, false);
        assertSame(retained, streamingSubscriber.retained);
        assertEquals(0, streamingSubscriber.flushed);
        assertEquals(1, streamingSubscriber.done);
    }

    // An exception thrown by an asynchronous error callback must not leave performPerPublisher blocked indefinitely.
    @Test(timeout = 5000)
    public void asynchronousStreamingErrorCallbackCannotStrandPerformPerPublisher() {
        // Set up a publisher that will trigger an error on a separate thread when subscribe() is called
        RuntimeException sourceFailure = new RuntimeException("source failed");
        RuntimeException callbackFailure = new RuntimeException("error callback failed");
        HitPublisher publisher = mock(HitPublisher.class);
        org.mockito.Mockito.doAnswer(call -> {
            HitSubscriber subscriber = call.getArgument(0);
            Thread callbackThread = new Thread(() -> subscriber.error(null, sourceFailure));
            callbackThread.setDaemon(true);
            callbackThread.start();
            return null;
        }).when(publisher).subscribe(any());

        // Set up a subscriber that throws an exception when its error() method is called,
        // simulating a failure in the error callback. We then make sure this doesn't leave
        // performPerPublisher blocked indefinitely.
        RecordingSubscriber subscriber = new RecordingSubscriber() {
            @Override public void error(LeafReaderContext lrc, Throwable exception) { throw callbackFailure; }
        };
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> HitsAbstract.performPerPublisher(List.of(publisher), () -> subscriber, false));
        assertSame(sourceFailure, exception.getCause());
    }

    // Late subscribers receive already-published hits as well as the batch held when the worker paused.
    @Test
    public void lateSubscriberReplaysAllPublishedHitsWithPendingBatch() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        final int NUMBER_OF_HITS = 102;
        MockSpans mockSpans = getMockSpans(NUMBER_OF_HITS);
        HitPublisherSpans publisher = publisher(executor, mockSpans);

        // Early subscriber wants only 100 hits, so the publisher will pause
        // after publishing 100 hits (the minimum amount that's always fetched) and holding 2 in a pending batch.
        final int EARLY_SUBSCRIBER_LIMIT = HitPublisherSpans.FETCH_HITS_MIN;
        RecordingSubscriber early = new RecordingSubscriber() {
            @Override public boolean needsMoreHits() { return hitsProcessed < EARLY_SUBSCRIBER_LIMIT; }
        };
        publisher.subscribe(early);
        executor.runNext();
        assertEquals(EARLY_SUBSCRIBER_LIMIT, early.hitsProcessed);

        // Late subscriber should receive all 102 hits, including the 2 that were held in the pending batch.\
        // Early also receives the final 2 hits.
        RecordingSubscriber late = new RecordingSubscriber();
        publisher.subscribe(late);
        executor.runNext();
        assertNull(late.error);
        assertEquals(NUMBER_OF_HITS, late.hitsProcessed);
        assertEquals(NUMBER_OF_HITS, early.hitsProcessed);
        assertEquals(1, late.done);
        assertEquals(1, early.done);
    }

    // Retained hits are visible before publication callbacks, and late subscribers see the same sequence.
    @Test
    public void retainedHitsAreReadyWhenSpansPublishes() throws IOException {
        // Set up publisher with 3 hits
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor);

        // Set up subscriber, subscribe, and run the worker
        RecordingSubscriber subscriber = new RecordingSubscriber();
        publisher.subscribe(subscriber);
        executor.runNext();

        // Check that everything is as expected
        assertNull(subscriber.error);
        int numHits = TEST_HITS.numberOfHits();
        assertEquals(numHits, subscriber.hitsProcessed);
        assertEquals(List.of((long)numHits), subscriber.retainedSizes);
        assertEquals(1, subscriber.done);
        List<String> lateEvents = List.of("start", "hits:0-" + numHits + "@0", "flush:" + numHits, "done");
        List<String> earlyEvents = new ArrayList<>(lateEvents);
        earlyEvents.add(1, "flush:0"); // early already receives flush before first hit
        assertEquals(earlyEvents, subscriber.events);

        // Check that late subscriber receives the same retained hits and expected series of events
        RecordingSubscriber late = new RecordingSubscriber();
        publisher.subscribe(late);
        assertEquals(numHits, late.hitsProcessed);
        assertEquals(1, late.done);
        assertEquals(lateEvents, late.events);
    }

    // Repeated activation while a worker is queued is coalesced into one scheduled worker.
    @Test
    public void repeatedActivationWhileQueuedSchedulesOneWorker() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor);

        // Create subscriber that initially doesn't want any hits yet.
        AtomicBoolean needMore = new AtomicBoolean();
        RecordingSubscriber subscriber = new RecordingSubscriber() {
            @Override public boolean needsMoreHits() { return needMore.get(); }
        };
        publisher.subscribe(subscriber);

        // Activate multiple times, but the executor should only have one pending task.
        for (int i = 0; i < 100; i++)
            publisher.activate();
        assertEquals(1, executor.pendingTasks());

        // Now allow the subscriber to request hits and run the worker. It should complete successfully.
        needMore.set(true);
        executor.runNext();
        assertEquals(1, subscriber.done);
        assertEquals(0, executor.pendingTasks());
    }

    // Demand appearing during the worker's pause recheck keeps that worker running to completion.
    @Test
    public void demandThatAppearsBeforeLockedPauseRecheckKeepsWorker() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor);

        // Subscriber that initially doesn't want any hits yet, but will request more during the pause recheck.
        // (see HitPublisherSpans#fetchAndPublishHits for the pause recheck logic - first one checks if the subscriber
        // needs more hits, then locks and checks again)
        AtomicInteger demandPolls = new AtomicInteger();
        RecordingSubscriber subscriber = new RecordingSubscriber() {
            @Override public boolean needsMoreHits() { return demandPolls.incrementAndGet() > 1; }
        };
        publisher.subscribe(subscriber);
        executor.runNext();

        // Check expected values
        assertTrue(demandPolls.get() >= 2);
        assertEquals(TEST_HITS.numberOfHits(), subscriber.hitsProcessed);
        assertEquals(1, subscriber.done);
        assertEquals(0, executor.pendingTasks());
    }

    // Activation racing with a pause decision must arrange another worker instead of leaving work idle.
    @Test
    public void activationDuringPauseDoesNotLeaveThePublisherIdle() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor);
        RecordingSubscriber subscriber = new RecordingSubscriber() {
            private boolean needMore;
            @Override public boolean needsMoreHits() { return needMore; }
            @Override public void flush(LeafReaderContext lrc, long published) {
                super.flush(lrc, published);
                if (!needMore) {
                    // Activation happens after the pause decision but before the fetch task has returned.
                    needMore = true;
                    publisher.activate();
                }
            }
        };
        publisher.subscribe(subscriber);
        executor.runNext();
        while (executor.pendingTasks() > 0)
            executor.runNext();
        assertNull(subscriber.error);
        assertEquals(1, subscriber.done);
        assertEquals(TEST_HITS.numberOfHits(), publisher.getStatic().size());
    }

    // If activation blocks on the final pause lock, it must schedule a successor after the current worker exits.
    @Test(timeout = 5000)
    public void activationBlockedByFinalPauseDecisionSchedulesSuccessor() throws Exception {
        // Set up publisher with simple TEST_HITS
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor);

        // Set up a subscriber that will request more hits during the pause recheck,
        // but only after the first pause recheck has been entered.
        // The two latches are used to coordinate the timing of the test: one to
        // signal that the pause recheck has been entered, and another to release it.
        CountDownLatch lockedRecheckEntered = new CountDownLatch(1);
        CountDownLatch releaseLockedRecheck = new CountDownLatch(1);
        AtomicBoolean needMore = new AtomicBoolean();
        AtomicInteger demandPolls = new AtomicInteger();
        RecordingSubscriber subscriber = new RecordingSubscriber() {
            @Override
            public boolean needsMoreHits() {
                if (demandPolls.incrementAndGet() == 2) { // ensure the second poll is the one that blocks
                    lockedRecheckEntered.countDown();
                    try {
                        releaseLockedRecheck.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    return false;
                }
                return needMore.get();
            }
        };
        publisher.subscribe(subscriber);

        // If the worker thread throws an exception, we want to capture it and fail the test.
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                executor.runNext();
            } catch (Throwable e) {
                threadFailure.set(e);
            }
        });
        worker.setDaemon(true);
        worker.start();

        // Wait for the first pause REcheck (second call to needsMoreHits) to be entered
        lockedRecheckEntered.await();
        needMore.set(true);

        // Activate the publisher in a separate thread, which will block on the pause recheck lock.
        Thread activator = new Thread(() -> {
            try {
                publisher.activate();
            } catch (Throwable e) {
                threadFailure.set(e);
            }
        });
        activator.setDaemon(true);
        activator.start();

        // Wait for the activator to reach the BLOCKED state
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (activator.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline)
            Thread.onSpinWait();
        Thread.State blockedState = activator.getState();

        // Release the lock, and wait for both threads to finish
        releaseLockedRecheck.countDown();
        worker.join();
        activator.join();

        // Check that the activator was indeed blocked and that no exceptions were thrown in either thread.
        assertEquals(Thread.State.BLOCKED, blockedState);
        assertNull(threadFailure.get());
        assertEquals(1, executor.pendingTasks());

        executor.runNext();
        assertEquals(1, subscriber.done);
        assertEquals(0, executor.pendingTasks());
    }

    // Completed and empty publishers both remain terminal and never queue work for later activation/subscription.
    @Test
    public void terminalPublishersCannotBeRescheduled() throws IOException {
        // Run the test with a spans producing 1 hit and one with 0 hits
        for (MockSpans spans: List.of(getMockSpans(1), getMockSpans(0))) {
            ManualExecutor executor = new ManualExecutor();
            HitPublisherSpans publisher = publisher(executor, spans);
            RecordingSubscriber subscriber = new RecordingSubscriber();
            publisher.subscribe(subscriber);
            executor.runNext();
            assertEquals(1, subscriber.done);
            for (int i = 0; i < 10; i++)
                publisher.activate();
            RecordingSubscriber late = new RecordingSubscriber();
            publisher.subscribe(late);
            assertEquals(1, late.done);
            assertEquals(0, executor.pendingTasks());
        }
    }

    // Rejected executor submissions release worker ownership so a later activation can retry successfully.
    @Test
    public void rejectedSubmissionReleasesWorkerOwnershipForRetry() throws IOException {
        ManualExecutor executor = new ManualExecutor() {
            private boolean reject = true;

            @Override
            public synchronized void execute(Runnable command) {
                if (reject) {
                    reject = false;
                    throw new RejectedExecutionException("first submission rejected");
                }
                super.execute(command);
            }
        };
        HitPublisherSpans publisher = publisher(executor);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        assertThrows(RejectedExecutionException.class, () -> publisher.subscribe(subscriber));
        assertEquals(0, executor.pendingTasks());
        publisher.activate();
        assertEquals(1, executor.pendingTasks());
        executor.runNext();
        assertEquals(1, subscriber.done);
    }

    // A subscriber joining during a callback receives the hits already published, without gaps or duplicates.
    @Test(timeout = 5000)
    public void subscriberJoiningDuringPublicationReplaysPublishedHits() throws Exception {
        // Set up batch of two hits
        Hits.HitsContext context = new Hits.HitsContext(new MockAnnotatedField());
        HitsMutable batch = HitsMutable.create(context, -1, false, false);
        batch.add(0, 0, 1, null);
        batch.add(1, 0, 1, null);

        // Set up a subcriber that will block in its hits() callback, allowing us to simulate a late subscriber
        // joining during the callback.
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        RecordingSubscriber early = new RecordingSubscriber() {
            @Override
            public void hits(LeafReaderContext lrc, Hits batch, long start, long end, int docs, long offset) {
                super.hits(lrc, batch, start, end, docs, offset);
                callbackEntered.countDown();
                try {
                    releaseCallback.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
        };
        HitPublisherOutput output = new HitPublisherOutput(context);
        output.subscribe(early);

        // Start a thread to publish the batch, which will block in the early subscriber's hits() callback.
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        Thread producer = new Thread(() -> {
            try {
                output.publish(batch, 2);
            } catch (Throwable e) {
                threadFailure.set(e);
            }
        });
        producer.start();

        // Wait for the early subscriber's hits() callback to be entered, then start a late subscriber in another thread.
        callbackEntered.await();
        RecordingSubscriber joining = new RecordingSubscriber();
        Thread subscriber = new Thread(() -> {
            try {
                output.subscribe(joining);
            } catch (Throwable e) {
                threadFailure.set(e);
            }
        });
        subscriber.start();

        // Release the early subscriber's callback so the producer can finish,
        // and wait for both threads to finish.
        releaseCallback.countDown();
        producer.join();
        subscriber.join();

        // Check that no exceptions were thrown in either thread, and that both subscribers received the
        // expected hits and flushes.
        assertNull(threadFailure.get());
        long numHits = batch.size();
        assertEquals(numHits, early.hitsProcessed);
        assertEquals(numHits, joining.hitsProcessed);
        assertEquals(List.of(numHits), early.retainedSizes);
        assertEquals(List.of(numHits), joining.retainedSizes);
        assertEquals(2, joining.flushed);
        output.flush();
        output.complete();
    }


    ///@@@@@@@@@@

    // Concurrent getStatic callers all unblock with the same fully completed retained result.
    @Test(timeout = 5000)
    public void multipleStaticWaitersShareTheCompletedRetainedView() throws Exception {
        Hits.HitsContext context = new Hits.HitsContext(new MockAnnotatedField());
        HitPublisherOutput output = new HitPublisherOutput(context);
        ExecutorService waiters = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        Future<Hits> first = waiters.submit(() -> { ready.countDown(); return output.getStatic(); });
        Future<Hits> second = waiters.submit(() -> { ready.countDown(); return output.getStatic(); });
        ready.await();
        HitsMutable batch = HitsMutable.create(context, -1, false, false);
        batch.add(0, 0, 1, null);
        output.publish(batch, 1);
        output.flush();
        output.complete();
        assertEquals(1, first.get().size());
        assertEquals(1, second.get().size());
        waiters.shutdownNow();
    }

    // Count-only updates are accumulated and replayed as totals to subscribers that join later.
    @Test
    public void lateSubscriberReceivesCumulativeCountOnlyTotals() {
        HitPublisherOutput output = new HitPublisherOutput(new Hits.HitsContext(new MockAnnotatedField()));
        RecordingSubscriber early = new RecordingSubscriber();
        output.subscribe(early);
        output.counted(2, 1);
        output.counted(3, 2);
        RecordingSubscriber late = new RecordingSubscriber();
        output.subscribe(late);
        assertEquals(5, early.hitsCounted);
        assertEquals(3, early.docsCounted);
        assertEquals(5, late.hitsCounted);
        assertEquals(3, late.docsCounted);
        output.flush();
        output.complete();
    }

    // Filtering adjusts published and late-subscriber counts, and does not forward unverifiable count-only totals.
    @Test
    public void filteredPublicationAndCatchUpUseFilteredCounts() {
        HitPublisher source = mock(HitPublisher.class);
        Hits.HitsContext context = new Hits.HitsContext(new MockAnnotatedField());
        when(source.context()).thenReturn(context);
        HitFilter filter = mock(HitFilter.class);
        when(filter.forSegment(any(), any(), any())).thenReturn(filter);
        when(filter.accept(1)).thenReturn(true);
        HitPublisherFilter publisher = new HitPublisherFilter(source, filter);
        var captor = org.mockito.ArgumentCaptor.forClass(HitSubscriber.class);
        org.mockito.Mockito.verify(source).subscribe(captor.capture());
        HitSubscriber input = captor.getValue();
        HitsMutable batch = HitsMutable.create(context, -1, false, false);
        batch.add(0, 0, 1, null);
        batch.add(0, 1, 2, null);
        RecordingSubscriber early = new RecordingSubscriber();
        publisher.subscribe(early);
        input.hits(null, batch, 0, 2, 1, 0);
        input.counted(7, 3); // cannot know how many would pass the filter, so this must not be forwarded
        RecordingSubscriber late = new RecordingSubscriber();
        publisher.subscribe(late);
        input.flush(null, 2);
        input.done(null);
        assertEquals(List.of(1L), early.retainedSizes);
        assertEquals(1, early.hitsProcessed);
        assertEquals(0, early.hitsCounted);
        assertEquals(1, early.flushed);
        assertEquals(1, late.hitsProcessed);
        assertEquals(0, late.hitsCounted);
        assertEquals(1, late.flushed);
        assertEquals(1, late.done);
        RecordingSubscriber completed = new RecordingSubscriber();
        publisher.subscribe(completed);
        assertEquals(1, completed.hitsProcessed);
        assertEquals(1, completed.done);
        assertEquals(List.of("start", "flush:0", "hits:0-1@0", "flush:1", "done"), early.events);
        assertEquals(List.of("start", "hits:0-1@0", "flush:1", "flush:1", "done"), late.events);
        assertEquals(List.of("start", "hits:0-1@0", "flush:1", "done"), completed.events);
    }
}
