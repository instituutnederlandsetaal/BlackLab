package nl.inl.blacklab.perdocument;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.docs.DocResult;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.hitresults.HitResults;

public class TestDocResults {

    private MockBlackLabIndex index;

    private DocResults drs;

    @Before
    public void setUp() {
        int[] aDoc = { 1, 1, 2, 3, 3 };
        int[] aStart = { 1, 2, 3, 4, 5 };
        int[] aEnd = { 2, 3, 4, 5, 6 };

        index = new MockBlackLabIndex();
        HitResults hitResults = HitResults.list(index.createDefaultQueryInfo(), aDoc, aStart, aEnd);
        drs = hitResults.perDocResults(Results.NO_LIMIT);
    }

    @Test
    public void testDocResultsIterate() {
        int[] expDoc = { 1, 2, 3 };
        int[] expHits = { 2, 1, 2 };
        int i = 0;
        for (DocResult dr : drs) {
            Assert.assertEquals(expDoc[i], (int)dr.identity().value());
            Assert.assertEquals(expHits[i], dr.size());
            i++;
        }
    }

    @After
    public void tearDown() {
        index.close();
    }

}
