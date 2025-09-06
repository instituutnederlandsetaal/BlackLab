package nl.inl.blacklab.search.results.hits.fetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.index.Term;
import org.junit.Test;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsFromFetcher;
import nl.inl.blacklab.search.results.hits.HitsUtils;
import nl.inl.blacklab.testutil.TestIndex;

public class TestHitsFromFetcher {
    public final TestIndex testIndex = TestIndex.get();

    @Test
    public void testParallelSearchInterrupt() {
        // if we interrupt too early the SpansReader will not run at all, so we wait until it has begun, before sending the interrupt.
        CountDownLatch waitForSpansReaderToStart = new CountDownLatch(1);
        // the source of the interrupt continues before the target thread receives it sometimes, so this is a way to block until it's been received.
        CountDownLatch waitForSpansReaderToBeInterrupted = new CountDownLatch(1);

        QueryInfo queryInfo = QueryInfo.create(testIndex.index());
        BLSpanTermQuery patternQuery = new BLSpanTermQuery(queryInfo, new Term("contents%word@i", "the"));
        SearchSettings searchSettings = SearchSettings.DEFAULT;
        HitFetcherQuery hitFetcher = new HitFetcherQuery(patternQuery, searchSettings);
        HitsFromFetcher h = new HitsFromFetcher(hitFetcher, HitFilter.ACCEPT_ALL);

        // Replace SpansReader workers in HitsFromQueryParallel with a mock that awaits an interrupt and then lets main thread know when it received it.
        HitFetcherQuery hitFetcherQuery = (HitFetcherQuery) h.hitFetcher;
        hitFetcherQuery.segmentReaders.clear();
        hitFetcherQuery.segmentReaders.add(new HitFetcherSegmentQuery(null, HitFetcherSegmentAbstract.State.DUMMY) {
            @Override
            public synchronized void run() {
                try {
                    // signal main thread we have started, so it can send the interrupt()
                    waitForSpansReaderToStart.countDown();
                    Thread.sleep(100_000); // wait for the interrupt() to arrive
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // preserve interrupted status
                    waitForSpansReaderToBeInterrupted.countDown(); // we got it! signal main thread again.
                }
            }
        });

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
        QueryInfo queryInfo = QueryInfo.create(testIndex.index());
        BLSpanTermQuery patternQuery = new BLSpanTermQuery(queryInfo, new Term("contents%word@i", "the"));
        HitFetcherQuery hitFetcher = new HitFetcherQuery(patternQuery, SearchSettings.DEFAULT);
        HitsFromFetcher h = new HitsFromFetcher(hitFetcher, HitFilter.ACCEPT_ALL);

        // Replace SpansReader workers in HitsFromQueryParallel with a mock that will just throw an exception.
        RuntimeException exceptionToThrow = new RuntimeException("TEST_SPANSREADER_CRASHED");
        HitFetcherQuery hitFetcherQuery = (HitFetcherQuery) h.hitFetcher;
        hitFetcherQuery.segmentReaders.clear();
        hitFetcherQuery.segmentReaders.add(new HitFetcherSegmentQuery(null, HitFetcherSegmentAbstract.State.DUMMY) {
            @Override
            public synchronized void run() { throw exceptionToThrow; }
        });

        Throwable thrownException = null;
        try {
            h.size();
        } catch (Exception e) {
            // get to the root cause, which should be the exception we threw in the SpansReader.
            thrownException = e;
            while (thrownException.getCause() != null) thrownException = thrownException.getCause();
        }

        assertEquals(thrownException, exceptionToThrow);
    }

    @Test
    public void testSublist() {
        QueryInfo queryInfo = QueryInfo.create(testIndex.index());
        BLSpanQuery patternQuery = new SpanQueryAnyToken(queryInfo, 1, 1, "contents%word@i");
        HitFetcherQuery hitFetcher = new HitFetcherQuery(patternQuery, SearchSettings.DEFAULT);
        Hits whole = new HitsFromFetcher(hitFetcher, HitFilter.ACCEPT_ALL);
        int subListStart = 11;
        int subListLength = 15;
        Hits sub = whole.sublist(subListStart, subListLength);
        assertEquals("sublist size", subListLength, sub.size());
        for (int i = 0; i < subListLength; i++) {
            assertEquals("sublist element " + i, whole.get(subListStart + i), sub.get(i));
        }
    }

    @Test
    public void testSort() {
        QueryInfo queryInfo = QueryInfo.create(testIndex.index());
        BLSpanQuery patternQuery = new SpanQueryAnyToken(queryInfo, 1, 1, "contents%word@i");
        HitsUtils.setThresholdSingleThreadedGroupAndSort(0); // test with multithreaded sorting
        HitFetcherQuery hitFetcher = new HitFetcherQuery(patternQuery, SearchSettings.DEFAULT);
        Hits unsorted = new HitsFromFetcher(hitFetcher, HitFilter.ACCEPT_ALL);
        HitProperty sortBy = new HitPropertyDocumentStoredField(testIndex.index(), "title");
        Hits sorted = unsorted.sorted(sortBy);
        assertEquals("same size", unsorted.size(), sorted.size());
        sortBy = sortBy.copyWith(sorted);
        for (int i = 1; i < sorted.size(); i++) {
            assertTrue("sorted element " + i, sortBy.compare(i - 1, i) <= 0);
        }
    }
}
