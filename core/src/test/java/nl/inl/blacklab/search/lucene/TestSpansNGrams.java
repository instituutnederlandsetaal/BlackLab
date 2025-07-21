package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.queries.spans.Spans;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

public class TestSpansNGrams {

    private static SpansNGrams getSpans() {
        // NOTE: in doc 1, all tokens except 0-1 match; in doc 3, all tokens match
        SpansNGrams spans = new SpansNGrams(null, "test", 2, 3);
        spans.setTest(1); // no IndexReader available
        return spans;
    }

    @Test
    public void testSpansNGrams() throws IOException {
        Spans exp = MockSpans.fromLists(
                new int[] { 0, 0, 0, 0, 0, 0, 0 },
                new int[] { 0, 0, 1, 1, 2, 2, 3 },
                new int[] { 2, 3, 3, 4, 4, 5, 5 });
        TestUtil.assertEquals(exp, getSpans());
    }

    @Test
    public void testSpansNGramsAdvance() throws IOException {
        SpansNGrams spans = getSpans();
        Assert.assertEquals(0, spans.nextDoc());
        // advance should always go to next doc
        Assert.assertEquals(DocIdSetIterator.NO_MORE_DOCS, spans.advance(1));
    }

}
