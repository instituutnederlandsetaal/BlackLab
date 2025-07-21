package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.queries.spans.Spans;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

public class TestSpansNot {

    private static SpansNot getSpans() {
        // NOTE: in doc 1, all tokens except 0-1 match; in doc 3, all tokens match
        BLSpans a = MockSpans.fromLists(
                new int[] { 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 3, 3, 3, 3, 3, 5 },
                new int[] { 0, 1, 2, 3, 1, 2, 3, 4, 1, 3, 0, 1, 2, 3, 4, 0 },
                new int[] { 1, 2, 3, 4, 2, 3, 4, 5, 2, 4, 1, 2, 3, 4, 5, 5 });
        SpansNot spans = new SpansNot(null, "test", a);
        spans.setTest(6); // no IndexReader available
        return spans;
    }

    @Test
    public void testSpansNot() throws IOException {
        Spans exp = MockSpans.fromLists(
                new int[] { 0, 1, 2, 2, 2, 4, 4, 4, 4, 4 },
                new int[] { 4, 0, 0, 2, 4, 0, 1, 2, 3, 4 },
                new int[] { 5, 1, 1, 3, 5, 1, 2, 3, 4, 5 });
        TestUtil.assertEquals(exp, getSpans());
    }

    @Test
    public void testSpansNotAdvance() throws IOException {
        Spans exp = MockSpans.fromLists(
                new int[] { 2, 2, 2, 4, 4, 4, 4, 4 },
                new int[] { 0, 2, 4, 0, 1, 2, 3, 4 },
                new int[] { 1, 3, 5, 1, 2, 3, 4, 5 });
        SpansNot spans = getSpans();
        spans.advance(2);
        TestUtil.assertEquals(exp, spans, true);
    }

    @Test
    public void testSpansNotAdvanceNoResults() throws IOException {
        Spans exp = MockSpans.emptySpans();
        SpansNot spans = getSpans();
        Assert.assertEquals(DocIdSetIterator.NO_MORE_DOCS, spans.advance(5));
        TestUtil.assertEquals(exp, spans, true);
    }

    @Test
    public void testSpansNotAdvanceBeyond() throws IOException {
        Spans exp = MockSpans.emptySpans();
        SpansNot spans = getSpans();
        Assert.assertEquals(DocIdSetIterator.NO_MORE_DOCS, spans.advance(1000));
        TestUtil.assertEquals(exp, spans, true);
    }

}
