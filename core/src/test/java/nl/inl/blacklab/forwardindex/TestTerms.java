package nl.inl.blacklab.forwardindex;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.testutil.TestIndex;

public class TestTerms {

    static TestIndex testIndexIntegrated;

    private TestIndex testIndex;

    private Terms terms;

    @BeforeClass
    public static void setUpClass() {
        testIndexIntegrated = TestIndex.getWithTestDelete();
    }

    @AfterClass
    public static void tearDownClass() {
        if (testIndexIntegrated != null)
            testIndexIntegrated.close();
    }

    @Before
    public void setUp() {
        testIndex = testIndexIntegrated;
        BlackLabIndex index = testIndex.index();
        Annotation ann = index.mainAnnotatedField().mainAnnotation();
        terms = testIndex.getTermsSegment(ann);
    }

    @Test
    public void testTerms() {
        for (int i = 0; i < terms.numberOfTerms(); i++) {
            String term = terms.get(i);
            int sortPos = terms.idToSortPosition(i, MatchSensitivity.SENSITIVE);
            int sortPos2 = terms.termToSortPosition(term, MatchSensitivity.SENSITIVE);
            Assert.assertEquals("Sensitive sort positions should be identical", sortPos, sortPos2);

            sortPos = terms.idToSortPosition(i, MatchSensitivity.INSENSITIVE);
            sortPos2 = terms.termToSortPosition(term, MatchSensitivity.INSENSITIVE);
            Assert.assertEquals("Insensitive sort positions should be identical", sortPos, sortPos2);
        }
    }

    /**
     * Initializing global terms while (common pool) threads are blocked waiting on the
     * TermsGlobal lock should not deadlock.
     */
    @Test(timeout = 60_000)
    public void testInitializeWhileCommonPoolThreadsWaitForLock() throws Exception {
        BlackLabIndex index = testIndex.index();
        Annotation ann = index.mainAnnotatedField().mainAnnotation();
        TermsGlobal termsGlobal = new TermsGlobal(ann.sensitivity(MatchSensitivity.SENSITIVE).luceneField());

        // Start initialization in a common pool thread, then make sure the other
        // common pool threads are all blocked waiting for the lock.
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CompletableFuture<Void> init = CompletableFuture.runAsync(() -> {
            synchronized (termsGlobal) {
                lockAcquired.countDown();
                try {
                    Thread.sleep(100); // give the other tasks a chance to block on the lock
                    termsGlobal.initialize(index.reader());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        lockAcquired.await();
        List<CompletableFuture<Integer>> waiters = new ArrayList<>();
        for (int i = 0; i < ForkJoinPool.getCommonPoolParallelism() * 2; i++) {
            waiters.add(CompletableFuture.supplyAsync(() -> {
                synchronized (termsGlobal) {
                    return termsGlobal.numberOfTerms();
                }
            }));
        }
        init.get();
        Terms expected = index.forwardIndex(ann).terms();
        Assert.assertEquals(expected.numberOfTerms(), termsGlobal.numberOfTerms());
        for (CompletableFuture<Integer> waiter: waiters)
            Assert.assertEquals(expected.numberOfTerms(), (int) waiter.get());
        for (int i = 0; i < expected.numberOfTerms(); i++) {
            String term = termsGlobal.get(i);
            Assert.assertEquals(term, expected.get(expected.indexOf(term, MatchSensitivity.SENSITIVE)));
        }
    }
}
