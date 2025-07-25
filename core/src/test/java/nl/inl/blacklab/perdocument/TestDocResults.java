package nl.inl.blacklab.perdocument;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Results;

public class TestDocResults {

    @Test
    public void testDocResultsIterate() {
        int[] aDoc = { 1, 1, 2, 3, 3 };
        int[] aStart = { 1, 2, 3, 4, 5 };
        int[] aEnd = { 2, 3, 4, 5, 6 };

        try (MockBlackLabIndex index = new MockBlackLabIndex()) {
            Hits hits = Hits.list(index.createDefaultQueryInfo(), aDoc, aStart, aEnd);
            DocResults drs = hits.perDocResults(Results.NO_LIMIT);
    
            int[] expDoc = { 1, 2, 3 };
            int[] expHits = { 2, 1, 2 };
            int i = 0;
            for (DocResult dr : drs) {
                Assert.assertEquals(expDoc[i], (int)dr.identity().value());
                Assert.assertEquals(expHits[i], dr.size());
                i++;
            }
        }
    }

}
