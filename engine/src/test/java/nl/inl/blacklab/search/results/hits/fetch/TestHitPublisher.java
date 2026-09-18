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
    private static class ManualExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override public synchronized void execute(Runnable command) { tasks.add(command); }
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

    private static class RecordingSubscriber implements HitSubscriber {
        Hits retained;
        long received;
        long counted;
        int countedDocs;
        long flushed;
        int done;
        Throwable error;
        final List<Long> retainedSizes = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        @Override public void start(LeafReaderContext lrc, Hits results) { retained = results; events.add("start"); }
        @Override public boolean needsMoreHits() { return true; }
        @Override public void counted(long hits, int docs) {
            counted += hits;
            countedDocs += docs;
            events.add("counted:" + hits + "/" + docs);
        }
        @Override public void hits(LeafReaderContext lrc, Hits batch, long start, long end, int docs, long offset) {
            received += end - start;
            retainedSizes.add(retained.size());
            events.add("hits:" + start + "-" + end + "@" + offset);
        }
        @Override public void flush(LeafReaderContext lrc, long published) {
            flushed = published;
            events.add("flush:" + published);
        }
        @Override public void done(LeafReaderContext lrc) { done++; events.add("done"); }
        @Override public void error(LeafReaderContext lrc, Throwable exception) {
            error = exception;
            events.add("error");
        }
    }

    private static HitPublisherSpans publisher(ExecutorService executor) throws IOException {
        return publisher(executor, new MockSpans(new int[] { 0, 0, 1 }, new int[] { 0, 1, 0 }, new int[] { 1, 2, 1 }));
    }

    private static HitPublisherSpans publisher(ExecutorService executor, MockSpans spans) throws IOException {
        BLSpanWeight weight = mock(BLSpanWeight.class);
        when(weight.getSpans(any(), any())).thenReturn(spans);
        return publisher(executor, weight);
    }

    private static HitPublisherSpans publisher(ExecutorService executor, BLSpanWeight weight) {
        return publisher(executor, weight, new ResultsStatsPassive(), new ResultsStatsPassive());
    }

    private static HitPublisherSpans publisher(ExecutorService executor, BLSpanWeight weight,
            ResultsStatsPassive hitsStats, ResultsStatsPassive docsStats) {
        return publisher(executor, weight, hitsStats, docsStats, true);
    }

    private static HitPublisherSpans publisher(ExecutorService executor, BLSpanWeight weight,
            ResultsStatsPassive hitsStats, ResultsStatsPassive docsStats, boolean retainHits) {
        LeafReaderContext lrc = mock(LeafReaderContext.class);
        when(lrc.reader()).thenReturn(mock(LeafReader.class));
        MockAnnotatedField field = new MockAnnotatedField();
        return new HitPublisherSpans(lrc, weight, new HitQueryContext(field.index(), null, field), executor,
                hitsStats, docsStats, retainHits);
    }

    @Test
    public void countOnlyBatchesAndFinalRemainderReachEarlyAndLateSubscribers() throws IOException {
        for (int n: List.of(3, 102)) {
            ManualExecutor executor = new ManualExecutor();
            int[] docs = java.util.stream.IntStream.range(0, n).toArray();
            int[] ends = java.util.stream.IntStream.range(0, n).map(i -> 1).toArray();
            BLSpanWeight weight = mock(BLSpanWeight.class);
            when(weight.getSpans(any(), any())).thenReturn(new MockSpans(docs, new int[n], ends));
            ResultsStatsPassive hitsStats = new ResultsStatsPassive(0, Long.MAX_VALUE);
            ResultsStatsPassive docsStats = new ResultsStatsPassive();
            HitPublisherSpans publisher = publisher(executor, weight, hitsStats, docsStats);
            RecordingSubscriber early = new RecordingSubscriber() {
                @Override public boolean needsMoreHits() { return counted < 101; }
            };
            publisher.subscribe(early);
            executor.runNext();
            assertEquals(Math.min(n, 101), early.counted);
            // The larger case pauses with one counted-but-unpublished hit; the smaller is already done.
            RecordingSubscriber late = new RecordingSubscriber();
            publisher.subscribe(late);
            while (executor.pendingTasks() > 0)
                executor.runNext();
            for (RecordingSubscriber subscriber: List.of(early, late)) {
                assertNull(subscriber.error);
                assertEquals(n, subscriber.counted);
                assertEquals(n, subscriber.countedDocs);
                assertEquals(0, subscriber.received);
                assertEquals(1, subscriber.done);
            }
            assertEquals(n, hitsStats.countedSoFar());
            assertEquals(n, docsStats.countedSoFar());
        }
    }

    @Test
    public void lateSubscribersReceiveTheTerminalNotification() {
        HitPublisherOutput output = new HitPublisherOutput(new Hits.HitsContext(new MockAnnotatedField()), true);
        output.flush();
        output.complete();
        RecordingSubscriber late = new RecordingSubscriber();
        output.subscribe(late);
        assertEquals(1, late.done);
        assertEquals(0, late.flushed);
        assertEquals(List.of("start", "flush:0", "done"), late.events);
    }

    @Test
    public void processingLimitPublishesStoredBatchBeforeCountOnlyRemainder() throws IOException {
        int n = 102;
        int[] docs = java.util.stream.IntStream.range(0, n).toArray();
        int[] ends = java.util.stream.IntStream.range(0, n).map(i -> 1).toArray();
        BLSpanWeight weight = mock(BLSpanWeight.class);
        when(weight.getSpans(any(), any())).thenReturn(new MockSpans(docs, new int[n], ends));
        ResultsStatsPassive hitsStats = new ResultsStatsPassive(1, Long.MAX_VALUE);
        ResultsStatsPassive docsStats = new ResultsStatsPassive();
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor, weight, hitsStats, docsStats);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        publisher.subscribe(subscriber);
        executor.runNext();
        assertEquals(100, subscriber.received);
        assertEquals(2, subscriber.counted);
        assertEquals(102, hitsStats.countedSoFar());
        assertEquals(100, hitsStats.processedSoFar());
        assertEquals(102, docsStats.countedSoFar());
        assertEquals(100, docsStats.processedSoFar());
        assertEquals(1, subscriber.done);
    }

    @Test
    public void lateSubscriberRequiresRetentionAfterSpansPublished() throws IOException {
        BLSpanWeight weight = mock(BLSpanWeight.class);
        when(weight.getSpans(any(), any())).thenReturn(
                new MockSpans(new int[] { 0 }, new int[] { 0 }, new int[] { 1 }));
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor, weight, new ResultsStatsPassive(),
                new ResultsStatsPassive(), false);
        RecordingSubscriber early = new RecordingSubscriber() {
            @Override
            public void hits(LeafReaderContext lrc, Hits batch, long start, long end, int docs, long offset) {
                received += end - start;
            }
        };
        publisher.subscribe(early);
        executor.runNext();
        assertEquals(1, early.received);
        assertEquals(1, early.done);
        RecordingSubscriber late = new RecordingSubscriber();
        assertThrows(IllegalStateException.class, () -> publisher.subscribe(late));
        assertEquals(List.of("start"), late.events);
    }

    @Test(timeout = 5000)
    public void failedSpansReleaseStaticWaitersAndNotifyLateSubscribers() throws IOException {
        RuntimeException failure = new IllegalStateException("spans failed");
        BLSpanWeight weight = mock(BLSpanWeight.class);
        when(weight.getSpans(any(), any())).thenThrow(failure);
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor, weight);
        RecordingSubscriber early = new RecordingSubscriber();
        publisher.subscribe(early);
        executor.runNext();
        assertSame(failure, early.error);
        assertSame(failure, assertThrows(RuntimeException.class, publisher::getStatic));
        RecordingSubscriber late = new RecordingSubscriber();
        publisher.subscribe(late);
        assertSame(failure, late.error);
        assertEquals(0, late.done);
        assertEquals(0, executor.pendingTasks());
        for (int i = 0; i < 10; i++)
            publisher.activate();
        assertEquals(0, executor.pendingTasks());
    }

    @Test(timeout = 5000)
    public void failedFiltersReleaseStaticWaitersAndNotifyLateSubscribers() {
        for (RuntimeException failure: List.of(new IllegalStateException("upstream failed"),
                new java.util.concurrent.CompletionException(new IllegalStateException("wrapped failure")))) {
            HitPublisher source = mock(HitPublisher.class);
            when(source.context()).thenReturn(new Hits.HitsContext(new MockAnnotatedField()));
            HitPublisherFilter publisher = new HitPublisherFilter(source, mock(HitFilter.class));
            var captor = org.mockito.ArgumentCaptor.forClass(HitSubscriber.class);
            org.mockito.Mockito.verify(source).subscribe(captor.capture());
            RecordingSubscriber early = new RecordingSubscriber();
            publisher.subscribe(early);
            captor.getValue().error(null, failure);
            assertSame(failure, early.error);
            assertSame(failure, assertThrows(RuntimeException.class, publisher::getStatic));
            RecordingSubscriber late = new RecordingSubscriber();
            publisher.subscribe(late);
            assertSame(failure, late.error);
            assertEquals(0, late.done);
        }
    }

    @Test
    public void groupingPropagatesAssertionErrors() {
        AssertionError failure = new AssertionError("query assertion failed");
        HitPublisher publisher = mock(HitPublisher.class);
        org.mockito.Mockito.doAnswer(call -> {
            HitSubscriber subscriber = call.getArgument(0);
            subscriber.error(null, failure);
            return null;
        }).when(publisher).subscribe(any());
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> HitsAbstract.performPerPublisher(List.of(publisher), RecordingSubscriber::new, false));
        assertSame(failure, exception.getCause());
    }

    @Test
    public void groupingCompletesForStaticAndAsynchronousStreamingPublishers() {
        Hits retained = Hits.empty(new Hits.HitsContext(new MockAnnotatedField()));
        HitPublisher staticSource = mock(HitPublisher.class);
        when(staticSource.getStatic()).thenReturn(retained);
        RecordingSubscriber staticSubscriber = new RecordingSubscriber();
        HitsAbstract.performPerPublisher(List.of(staticSource), () -> staticSubscriber, true);
        assertEquals(1, staticSubscriber.done);

        HitPublisher streamingSource = mock(HitPublisher.class);
        RecordingSubscriber streamingSubscriber = new RecordingSubscriber();
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
        HitsAbstract.performPerPublisher(List.of(streamingSource), () -> streamingSubscriber, false);
        assertSame(retained, streamingSubscriber.retained);
        assertEquals(0, streamingSubscriber.flushed);
        assertEquals(1, streamingSubscriber.done);
    }

    @Test(timeout = 5000)
    public void asynchronousStreamingErrorCallbackCannotStrandGrouping() {
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
        RecordingSubscriber subscriber = new RecordingSubscriber() {
            @Override public void error(LeafReaderContext lrc, Throwable exception) { throw callbackFailure; }
        };
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> HitsAbstract.performPerPublisher(List.of(publisher), () -> subscriber, false));
        assertSame(sourceFailure, exception.getCause());
    }

    @Test
    public void lateSubscriberReplaysAllPublishedHitsWithPendingBatch() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        int[] docs = java.util.stream.IntStream.range(0, 102).toArray();
        int[] ends = java.util.stream.IntStream.range(0, 102).map(i -> 1).toArray();
        HitPublisherSpans publisher = publisher(executor, new MockSpans(docs, new int[102], ends));
        RecordingSubscriber early = new RecordingSubscriber() {
            @Override public boolean needsMoreHits() { return received < 100; }
        };
        publisher.subscribe(early);
        executor.runNext(); // 100 published hits, one buffered hit, and one prefetched hit
        assertEquals(100, early.received);
        RecordingSubscriber late = new RecordingSubscriber();
        publisher.subscribe(late);
        executor.runNext();
        assertNull(late.error);
        assertEquals(102, late.received);
        assertEquals(1, late.done);
    }

    @Test
    public void retainedHitsAreReadyWhenSpansPublishes() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        publisher.subscribe(subscriber);
        executor.runNext();
        assertNull(subscriber.error);
        assertEquals(3, subscriber.received);
        assertEquals(List.of(3L), subscriber.retainedSizes);
        assertEquals(1, subscriber.done);
        assertEquals(List.of("start", "flush:0", "hits:0-3@0", "flush:3", "done"), subscriber.events);
        RecordingSubscriber late = new RecordingSubscriber();
        publisher.subscribe(late);
        assertEquals(3, late.received);
        assertEquals(1, late.done);
        assertEquals(List.of("start", "hits:0-3@0", "flush:3", "done"), late.events);
    }

    @Test
    public void repeatedActivationWhileQueuedSchedulesOneWorker() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor);
        AtomicBoolean needMore = new AtomicBoolean();
        RecordingSubscriber subscriber = new RecordingSubscriber() {
            @Override public boolean needsMoreHits() { return needMore.get(); }
        };
        publisher.subscribe(subscriber);
        for (int i = 0; i < 100; i++)
            publisher.activate();
        assertEquals(1, executor.pendingTasks());

        needMore.set(true);
        executor.runNext();
        assertEquals(1, subscriber.done);
        assertEquals(0, executor.pendingTasks());
    }

    @Test
    public void demandThatAppearsBeforeLockedPauseRecheckKeepsWorker() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor);
        AtomicInteger demandPolls = new AtomicInteger();
        RecordingSubscriber subscriber = new RecordingSubscriber() {
            @Override public boolean needsMoreHits() { return demandPolls.incrementAndGet() > 1; }
        };
        publisher.subscribe(subscriber);
        executor.runNext();
        assertTrue(demandPolls.get() >= 2);
        assertEquals(3, subscriber.received);
        assertEquals(1, subscriber.done);
        assertEquals(0, executor.pendingTasks());
    }

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
        assertEquals(3, publisher.getStatic().size());
    }

    @Test(timeout = 5000)
    public void activationBlockedByFinalPauseDecisionSchedulesSuccessor() throws Exception {
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = publisher(executor);
        CountDownLatch lockedRecheckEntered = new CountDownLatch(1);
        CountDownLatch releaseLockedRecheck = new CountDownLatch(1);
        AtomicBoolean needMore = new AtomicBoolean();
        AtomicInteger demandPolls = new AtomicInteger();
        RecordingSubscriber subscriber = new RecordingSubscriber() {
            @Override
            public boolean needsMoreHits() {
                if (demandPolls.incrementAndGet() == 2) {
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
        lockedRecheckEntered.await();
        needMore.set(true);
        Thread activator = new Thread(() -> {
            try {
                publisher.activate();
            } catch (Throwable e) {
                threadFailure.set(e);
            }
        });
        activator.setDaemon(true);
        activator.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (activator.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline)
            Thread.onSpinWait();
        Thread.State blockedState = activator.getState();
        releaseLockedRecheck.countDown();
        worker.join();
        activator.join();
        assertEquals(Thread.State.BLOCKED, blockedState);
        assertNull(threadFailure.get());
        assertEquals(1, executor.pendingTasks());

        executor.runNext();
        assertEquals(1, subscriber.done);
        assertEquals(0, executor.pendingTasks());
    }

    @Test
    public void terminalPublishersCannotBeRescheduled() throws IOException {
        for (MockSpans spans: List.of(
                new MockSpans(new int[] { 0 }, new int[] { 0 }, new int[] { 1 }),
                new MockSpans(new int[0], new int[0], new int[0]))) {
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

    @Test(timeout = 5000)
    public void subscriberJoiningDuringPublicationReplaysOneCoherentPrefix() throws Exception {
        Hits.HitsContext context = new Hits.HitsContext(new MockAnnotatedField());
        HitPublisherOutput output = new HitPublisherOutput(context, true);
        HitsMutable batch = HitsMutable.create(context, -1, false, false);
        batch.add(0, 0, 1, null);
        batch.add(1, 0, 1, null);
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
        output.subscribe(early);
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        Thread producer = new Thread(() -> {
            try {
                output.publish(batch, 2);
            } catch (Throwable e) {
                threadFailure.set(e);
            }
        });
        producer.start();
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
        releaseCallback.countDown();
        producer.join();
        subscriber.join();
        assertNull(threadFailure.get());
        assertEquals(2, early.received);
        assertEquals(2, joining.received);
        assertEquals(List.of(2L), early.retainedSizes);
        assertEquals(List.of(2L), joining.retainedSizes);
        assertEquals(2, joining.flushed);
        output.flush();
        output.complete();
        assertEquals(List.of("start", "flush:0", "hits:0-2@0", "flush:2", "done"), early.events);
        assertEquals(List.of("start", "hits:0-2@0", "flush:2", "flush:2", "done"), joining.events);
    }

    @Test(timeout = 5000)
    public void multipleStaticWaitersShareTheCompletedRetainedView() throws Exception {
        Hits.HitsContext context = new Hits.HitsContext(new MockAnnotatedField());
        HitPublisherOutput output = new HitPublisherOutput(context, true);
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

    @Test
    public void lateSubscriberReceivesCumulativeCountOnlyTotals() {
        HitPublisherOutput output = new HitPublisherOutput(new Hits.HitsContext(new MockAnnotatedField()), true);
        RecordingSubscriber early = new RecordingSubscriber();
        output.subscribe(early);
        output.counted(2, 1);
        output.counted(3, 2);
        RecordingSubscriber late = new RecordingSubscriber();
        output.subscribe(late);
        assertEquals(5, early.counted);
        assertEquals(3, early.countedDocs);
        assertEquals(5, late.counted);
        assertEquals(3, late.countedDocs);
        output.flush();
        output.complete();
    }

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
        assertEquals(1, early.received);
        assertEquals(0, early.counted);
        assertEquals(1, early.flushed);
        assertEquals(1, late.received);
        assertEquals(0, late.counted);
        assertEquals(1, late.flushed);
        assertEquals(1, late.done);
        RecordingSubscriber completed = new RecordingSubscriber();
        publisher.subscribe(completed);
        assertEquals(1, completed.received);
        assertEquals(1, completed.done);
        assertEquals(List.of("start", "flush:0", "hits:0-1@0", "flush:1", "done"), early.events);
        assertEquals(List.of("start", "hits:0-1@0", "flush:1", "flush:1", "done"), late.events);
        assertEquals(List.of("start", "hits:0-1@0", "flush:1", "done"), completed.events);
    }
}
