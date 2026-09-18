package nl.inl.blacklab.search.results.hits;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.Test;

import nl.inl.blacklab.mocks.MockAnnotatedField;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hits.fetch.HitPublisher;
import nl.inl.blacklab.search.results.hits.fetch.HitSubscriber;

public class TestHitsFromPublishers {

    private static final class ControlledPublisher implements HitPublisher {
        private final Hits.HitsContext context = new Hits.HitsContext(new MockAnnotatedField());
        private final boolean finish;
        private HitSubscriber subscriber;
        private boolean published;
        private int activations;

        private ControlledPublisher(boolean finish) {
            this.finish = finish;
        }

        @Override
        public void subscribe(HitSubscriber subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void activate() {
            activations++;
            if (published)
                return;
            published = true;
            Hits hit = Hits.single(context, 0, 0, 1);
            subscriber.start(null, hit);
            subscriber.hits(null, hit, 0, 1, 1, 0);
            subscriber.flush(null, 1);
            if (finish)
                subscriber.done(null);
        }

        @Override
        public Hits.HitsContext context() {
            return context;
        }

        @Override
        public Hits getStatic() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ManualPublisher implements HitPublisher {
        private final Hits.HitsContext context = new Hits.HitsContext(new MockAnnotatedField());
        private final Hits hit = Hits.single(context, 0, 0, 1);
        private final CountDownLatch activated = new CountDownLatch(1);
        private HitSubscriber subscriber;

        @Override
        public void subscribe(HitSubscriber subscriber) {
            this.subscriber = subscriber;
            subscriber.start(null, hit);
        }

        @Override
        public void activate() {
            activated.countDown();
        }

        @Override
        public Hits.HitsContext context() {
            return context;
        }

        @Override
        public Hits getStatic() {
            throw new UnsupportedOperationException();
        }

        void publishHitAndFinish() {
            subscriber.hits(null, hit, 0, 1, 1, 0);
            subscriber.flush(null, 1);
            subscriber.done(null);
        }
    }

    @Test(timeout = 1_000)
    public void publishedHitsContributeToCountedTotal() {
        ControlledPublisher publisher = new ControlledPublisher(true);
        HitsFromPublishers hits = new HitsFromPublishers(List.of(publisher), SearchSettings.DEFAULT);

        assertEquals(1, hits.resultsStats().countedTotal());
    }

    @Test(timeout = 1_000)
    public void publishedHitsSatisfyCountDemand() {
        ControlledPublisher publisher = new ControlledPublisher(false);
        HitsFromPublishers hits = new HitsFromPublishers(List.of(publisher), SearchSettings.DEFAULT);

        assertTrue(hits.sizeAtLeast(1));
        assertFalse(publisher.subscriber.needsMoreHits());
    }

    @Test(timeout = 1_000)
    public void readingPublishedPrefixDoesNotReactivatePublishers() {
        ControlledPublisher publisher = new ControlledPublisher(false);
        HitsFromPublishers hits = new HitsFromPublishers(List.of(publisher), SearchSettings.DEFAULT);
        assertTrue(hits.sizeAtLeast(1));
        assertEquals(1, publisher.activations);

        for (int i = 0; i < 100; i++) {
            assertEquals(0, hits.doc(0));
            assertEquals(0, hits.start(0));
            assertEquals(1, hits.end(0));
            assertEquals(0, hits.iterator().next().doc());
        }

        assertEquals(1, publisher.activations);
        assertFalse(publisher.subscriber.needsMoreHits());
    }

    @Test(timeout = 1_000)
    public void invalidSublistDoesNotWaitForMoreResultsWhileHoldingReadLock() {
        ControlledPublisher publisher = new ControlledPublisher(false);
        HitsFromPublishers hits = new HitsFromPublishers(List.of(publisher), SearchSettings.DEFAULT);
        assertTrue(hits.sizeAtLeast(1));

        assertThrows(IndexOutOfBoundsException.class, () -> hits.sublist(-1, 2));
        assertEquals(1, publisher.activations);
    }

    @Test(timeout = 2_000)
    public void countOnlyProgressWakesWaiter() throws Exception {
        ManualPublisher publisher = new ManualPublisher();
        HitsFromPublishers hits = new HitsFromPublishers(List.of(publisher), SearchSettings.get(0, 1));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Long> counted = executor.submit(() -> hits.resultsStats().countedTotal());
            assertTrue(publisher.activated.await(1, TimeUnit.SECONDS));

            publisher.subscriber.counted(1, 1);

            assertEquals(1, counted.get(1, TimeUnit.SECONDS).longValue());
            publisher.subscriber.done(null);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(timeout = 2_000)
    public void waitsForSignalsWithoutTimedPolling() throws Exception {
        ManualPublisher publisher = new ManualPublisher();
        HitsFromPublishers hits = new HitsFromPublishers(List.of(publisher), SearchSettings.DEFAULT);
        AtomicReference<Thread> waitingThread = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "hits-from-publishers-waiter");
            waitingThread.set(thread);
            return thread;
        });
        try {
            Future<Boolean> available = executor.submit(() -> hits.sizeAtLeast(1));
            assertTrue(publisher.activated.await(1, TimeUnit.SECONDS));
            assertTrue(awaitCondition(() -> waitingThread.get().getState() == Thread.State.WAITING));

            publisher.publishHitAndFinish();

            assertTrue(available.get(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(timeout = 2_000)
    public void firstConcurrentFailureRemainsTheReportedCause() throws Exception {
        ManualPublisher firstPublisher = new ManualPublisher();
        ManualPublisher secondPublisher = new ManualPublisher();
        HitsFromPublishers hits = new HitsFromPublishers(List.of(firstPublisher, secondPublisher), SearchSettings.DEFAULT);
        Throwable first = new IllegalStateException("first");
        Throwable second = new IllegalArgumentException("second");
        CyclicBarrier startTogether = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<Throwable> waitingSearch = executor.submit(() -> reportedCause(hits));
            assertTrue(firstPublisher.activated.await(1, TimeUnit.SECONDS));
            assertTrue(secondPublisher.activated.await(1, TimeUnit.SECONDS));
            Future<?> firstFailure = executor.submit(() -> failAtBarrier(startTogether, firstPublisher, first));
            Future<?> secondFailure = executor.submit(() -> failAtBarrier(startTogether, secondPublisher, second));
            startTogether.await(1, TimeUnit.SECONDS);
            firstFailure.get(1, TimeUnit.SECONDS);
            secondFailure.get(1, TimeUnit.SECONDS);

            Throwable reported = waitingSearch.get(1, TimeUnit.SECONDS);
            assertTrue(reported == first || reported == second);
            assertSame(reported, reportedCause(hits));
        } finally {
            executor.shutdownNow();
        }
    }

    private static void failAtBarrier(CyclicBarrier barrier, ManualPublisher publisher, Throwable failure) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        publisher.subscriber.error(null, failure);
    }

    private static Throwable reportedCause(HitsFromPublishers hits) {
        try {
            hits.size();
            fail("Expected publisher failure");
            return null;
        } catch (RuntimeException e) {
            return e.getCause();
        }
    }

    private static boolean awaitCondition(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 100 && !condition.getAsBoolean(); i++)
            Thread.sleep(5);
        return condition.getAsBoolean();
    }
}
