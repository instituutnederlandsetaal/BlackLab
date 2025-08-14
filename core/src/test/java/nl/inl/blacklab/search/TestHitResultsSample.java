package nl.inl.blacklab.search;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hits.EphemeralHit;

public class TestHitResultsSample {

    private static final int[] A_DOC = { 1, 1, 2, 3, 3, 3 };
    private static final int[] A_START = { 1, 4, 2, 1, 3, 5 };
    private static final int[] A_END = { 2, 5, 3, 2, 4, 7 };

    private static void assertSample(int[] expected, SampleParameters param) {
        try (MockBlackLabIndex index = new MockBlackLabIndex()) {
            HitResults hitResults = HitResults.list(index.createDefaultQueryInfo(), A_DOC, A_START, A_END).sample(param);
            int i = 0;
            Assert.assertEquals(expected.length, hitResults.size());
            for (EphemeralHit hit: hitResults.getHits()) {
                Assert.assertEquals(A_DOC[expected[i]], hit.doc());
                Assert.assertEquals(A_START[expected[i]], hit.start());
                Assert.assertEquals(A_END[expected[i]], hit.end());
                i++;
            }
        }
    }

    @Test
    public void testSample() {
        assertSample(new int[0], SampleParameters.percentage(0, 0));
        assertSample(new int[] { 3 }, SampleParameters.percentage(0.1667f, 1));
        assertSample(new int[] { 0, 1, 5 }, SampleParameters.percentage(0.5f, 1337));
        assertSample(new int[] { 0, 1, 2, 3 }, SampleParameters.percentage(0.6667f, 42));
        assertSample(new int[] { 0, 1, 2, 3, 4, 5 }, SampleParameters.percentage(1f, Long.MAX_VALUE));

        assertSample(new int[0], SampleParameters.fixedNumber(0, 0));
        assertSample(new int[] { 3 }, SampleParameters.fixedNumber(1, 1));
        assertSample(new int[] { 0, 1, 5 }, SampleParameters.fixedNumber(3, 1337));
        assertSample(new int[] { 0, 1, 2, 3 }, SampleParameters.fixedNumber(4, 42));
        assertSample(new int[] { 0, 1, 2, 3, 4, 5 }, SampleParameters.fixedNumber(6, Long.MAX_VALUE));
    }

}
