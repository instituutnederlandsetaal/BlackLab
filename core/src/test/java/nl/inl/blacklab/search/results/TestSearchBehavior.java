package nl.inl.blacklab.search.results;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.index.Term;
import org.junit.Test;

import nl.inl.blacklab.search.BlackLabIndex.IndexType;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.testutil.TestIndex;

public class TestSearchBehavior {
    public final TestIndex testIndex = TestIndex.get(IndexType.INTEGRATED);

    @Test
    public void testParallelSearchInterrupt() {
        // if we interrupt too early the SpansReader will not run at all, so we wait until it has begun, before sending the interrupt.
        CountDownLatch waitForSpansReaderToStart = new CountDownLatch(1);
        // the source of the interrupt continues before the target thread receives it sometimes, so this is a way to block until it's been received.
        CountDownLatch waitForSpansReaderToBeInterrupted = new CountDownLatch(1);

        BLSpanTermQuery patternQuery = new BLSpanTermQuery(null, new Term("contents%word@i", "the"));
        HitsFromQuery h = new HitsFromQuery(QueryInfo.create(testIndex.index()), patternQuery, SearchSettings.defaults());

        // Replace SpansReader workers in HitsFromQueryParallel with a mock that awaits an interrupt and then lets main thread know when it received it.
        h.spansReaders.clear();
        h.spansReaders.add(new SpansReader(null, null, null, null, null, null, null, null, null, null) {
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
        try { h.ensureResultsRead(-1); }
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
        BLSpanTermQuery patternQuery = new BLSpanTermQuery(null, new Term("contents%word@i", "the"));
        HitsFromQuery h = new HitsFromQuery(QueryInfo.create(testIndex.index()), patternQuery, SearchSettings.defaults());

        // Replace SpansReader workers in HitsFromQueryParallel with a mock that will just throw an exception.
        RuntimeException exceptionToThrow = new RuntimeException("TEST_SPANSREADER_CRASHED");
        h.spansReaders.clear();
        h.spansReaders.add(new SpansReader(null, null, null, null, null, null, null, null, null, null) {
            @Override
            public synchronized void run() { throw exceptionToThrow; }
        });

        Throwable thrownException = null;
        try {
            h.ensureResultsRead(-1);
        } catch (Exception e) {
            // get to the root cause, which should be the exception we threw in the SpansReader.
            thrownException = e;
            while (thrownException.getCause() != null) thrownException = thrownException.getCause();
        }

        assertEquals(thrownException, exceptionToThrow);
    }
}
