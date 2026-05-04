package nl.inl.blacklab.search.results.hits.fetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreMode;
import org.junit.Test;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsFromPublishers;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;
import nl.inl.blacklab.testutil.TestIndex;

public class TestHitsFromFetcher {
    public static final Hits.HitsContext DUMMY_CONTEXT = new Hits.HitsContext(null, MatchInfoDefs.EMPTY, null);

    public final TestIndex testIndex = TestIndex.get();

    @Test
    public void testParallelSearchInterrupt() {
        // if we interrupt too early the SpansReader will not run at all, so we wait until it has begun, before sending the interrupt.
        CountDownLatch waitForSpansReaderToStart = new CountDownLatch(1);
        // the source of the interrupt continues before the target thread receives it sometimes, so this is a way to block until it's been received.
        CountDownLatch waitForSpansReaderToBeInterrupted = new CountDownLatch(1);

        HitPublisher publisher = new HitPublisher() {

            @Override
            public void subscribe(HitSubscriber subscriber) {
                // nothing to do here
            }

            @Override
            public synchronized void activate() {
                Thread thread = new Thread(() -> {
                    try {
                        // signal main thread we have started, so it can send the interrupt()
                        waitForSpansReaderToStart.countDown();
                        Thread.sleep(100_000); // wait for the interrupt() to arrive
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // preserve interrupted status
                        waitForSpansReaderToBeInterrupted.countDown(); // we got it! signal main thread again.
                    }
                });
                thread.setUncaughtExceptionHandler((thread1, throwable) -> {});
                thread.start();
            }

            @Override
            public Hits.HitsContext context() {
                return DUMMY_CONTEXT;
            }

            @Override
            public Hits getStatic() {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };
        HitsFromPublishers h = new HitsFromPublishers(List.of(publisher), SearchSettings.DEFAULT);

        // Set up the interrupt.
        Thread hitsFromQueryParallelThread = Thread.currentThread();
        ForkJoinPool.commonPool().submit(() -> {
            try {
                waitForSpansReaderToStart.await();
                hitsFromQueryParallelThread.interrupt();
            } catch (InterruptedException e) {
                // never happens unless thread is interrupted during await(),
                // which only happens when test is aborted/shut down prematurely.
                Thread.currentThread().interrupt(); // preserve interrupted status
            }
        });

        // start the to-be-interrupted work.
        try { h.size(); }
        catch (Exception e) {
            // probably InterruptedException, but we don't care about that here.
        }

        try {
            // wait for the worker thread to be interrupted (it may take a few ms).
            assertTrue(
                    "SpansReader received Interrupt() within 1 second of interrupting parent SearchFromQueryParallel.",
                    waitForSpansReaderToBeInterrupted.await(1_000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            // await was interrupted, test suite probably shutting down.
            Thread.currentThread().interrupt(); // preserve interrupted status
        }
    }

    /** Test that an exception thrown from the SpansReader in a worker thread is correctly propagated to the main HitsFromQueryParallel thread */
    @Test
    public void testParallelSearchException() {
        RuntimeException exceptionToThrow = new RuntimeException("TEST_SPANSREADER_CRASHED");
        HitPublisher publisher = new HitPublisher() {

            private HitSubscriber subscriber;

            @Override
            public void subscribe(HitSubscriber subscriber) {
                this.subscriber = subscriber;
            }

            @Override
            public void activate() {
                Thread thread = new Thread(() -> {
                    subscriber.error(null, exceptionToThrow);
                });
                thread.start();
            }

            @Override
            public Hits.HitsContext context() {
                return DUMMY_CONTEXT;
            }

            @Override
            public Hits getStatic() {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };
        HitsFromPublishers h = new HitsFromPublishers(List.of(publisher), SearchSettings.DEFAULT);
        Throwable thrownException = null;
        try {
            h.size();
        } catch (Exception e) {
            // get to the root cause, which should be the exception we threw in the SpansReader.
            thrownException = e;
            while (thrownException.getCause() != null)
                thrownException = thrownException.getCause();
        }

        assertEquals(thrownException, exceptionToThrow);
    }

    @Test
    public void testSublist() throws IOException {
        BlackLabIndex index = testIndex.index();
        QueryInfo queryInfo = QueryInfo.create(index);
        BLSpanQuery patternQuery = new SpanQueryAnyToken(queryInfo, 1, 1, "contents%word@i");
        List<HitPublisher> publishers = getPublishers(queryInfo, patternQuery);
        Hits whole = new HitsFromPublishers(publishers, SearchSettings.DEFAULT);
        int subListStart = 11;
        int subListLength = 15;
        Hits sub = whole.sublist(subListStart, subListLength);
        assertEquals("sublist size", subListLength, sub.size());
        for (int i = 0; i < subListLength; i++) {
            assertEquals("sublist element " + i, whole.get(subListStart + i), sub.get(i));
        }
    }

    private static List<HitPublisher> getPublishers(QueryInfo queryInfo, BLSpanQuery patternQuery)
            throws IOException {
        BlackLabIndex index = queryInfo.index();
        BLSpanWeight weight = patternQuery.createWeight(index.searcher(),
                ScoreMode.COMPLETE_NO_SCORES, 1.0f);
        List<HitPublisher> publishers = new ArrayList<>();
        ExecutorService service = Executors.newFixedThreadPool(2);
        HitQueryContext hitQueryContext = new HitQueryContext(index, null, queryInfo.field());
        ResultsStatsPassive hitsStats = new ResultsStatsPassive();
        ResultsStatsPassive docsStats = new ResultsStatsPassive();
        for (LeafReaderContext lrc: index.reader().leaves()) {
            publishers.add(new HitPublisherSpans(lrc, weight, hitQueryContext, service, hitsStats, docsStats, true));
        }
        return publishers;
    }

    @Test
    public void testSort() throws IOException {
        QueryInfo queryInfo = QueryInfo.create(testIndex.index());
        BLSpanQuery patternQuery = new SpanQueryAnyToken(queryInfo, 1, 1, "contents%word@i");
        List<HitPublisher> publishers = getPublishers(queryInfo, patternQuery);
        Hits unsorted = new HitsFromPublishers(publishers, SearchSettings.DEFAULT);
        HitProperty sortBy = new HitPropertyDocumentStoredField(testIndex.index(), "title");
        Hits sorted = unsorted.sorted(sortBy);
        assertEquals("same size", unsorted.size(), sorted.size());
        sortBy = sortBy.copyWith(sorted);
        for (int i = 1; i < sorted.size(); i++) {
            assertTrue("sorted element " + i, sortBy.compare(i - 1, i) <= 0);
        }
    }
}
