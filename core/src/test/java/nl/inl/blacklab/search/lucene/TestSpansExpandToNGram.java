package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.queries.spans.Spans;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

public class TestSpansExpandToNGram {

    @Test
    public void testNGramsContaining() throws IOException {
        BLSpans a = new MockSpans(
                new int[] { 1, 2 },
                new int[] { 1, 2 },
                new int[] { 2, 3 });

        SpansFilterNGramsRaw spans = new SpansFilterNGramsRaw(null, "test", a,
                SpanQueryPositionFilter.Operation.CONTAINING, 2, 3, 0, 0);
        spans.setTest(true); // for test, all documents are 5 long

        Spans exp = new MockSpans(
                new int[] { 1, 1, 1, 1, 2, 2, 2, 2, 2 },
                new int[] { 0, 0, 1, 1, 0, 1, 1, 2, 2 },
                new int[] { 2, 3, 3, 4, 3, 3, 4, 4, 5 });
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testNGramsContainingAtEnd() throws IOException {
        BLSpans a = new MockSpans(
                new int[] { 1, 2 },
                new int[] { 1, 2 },
                new int[] { 2, 3 });

        SpansFilterNGramsRaw spans = new SpansFilterNGramsRaw(null, "test", a,
                SpanQueryPositionFilter.Operation.CONTAINING_AT_END, 2, 3, 0, 0);
        spans.setTest(true); // for test, all documents are 5 long

        Spans exp = new MockSpans(
                new int[] { 1, 2, 2 },
                new int[] { 0, 0, 1 },
                new int[] { 2, 3, 3 });
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testNGramsContainingAtStart() throws IOException {
        BLSpans a = new MockSpans(
                new int[] { 1, 2 },
                new int[] { 1, 2 },
                new int[] { 2, 3 });

        SpansFilterNGramsRaw spans = new SpansFilterNGramsRaw(null, "test", a,
                SpanQueryPositionFilter.Operation.CONTAINING_AT_START, 2, 3, 0, 0);
        spans.setTest(true); // for test, all documents have the same length

        Spans exp = new MockSpans(
                new int[] { 1, 1, 2, 2 },
                new int[] { 1, 1, 2, 2 },
                new int[] { 3, 4, 4, 5 });
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testNGramsWithin() throws IOException {
        BLSpans a = new MockSpans(
                new int[] { 1, 2 },
                new int[] { 1, 2 },
                new int[] { 5, 5 });

        SpansFilterNGramsRaw spans = new SpansFilterNGramsRaw(null, "test", a,
                SpanQueryPositionFilter.Operation.WITHIN, 2, 3, 0, 0);
        spans.setTest(true); // for test, all documents are 5 long

        Spans exp = new MockSpans(
                new int[] { 1, 1, 1, 1, 1, 2, 2, 2 },
                new int[] { 1, 1, 2, 2, 3, 2, 2, 3 },
                new int[] { 3, 4, 4, 5, 5, 4, 5, 5 });
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testNGramsMatches() throws IOException {
        BLSpans a = new MockSpans(
                new int[] { 1, 1, 2, 2 },
                new int[] { 1, 1, 2, 2 },
                new int[] { 2, 4, 4, 6 });

        SpansFilterNGramsRaw spans = new SpansFilterNGramsRaw(null, "test", a,
                SpanQueryPositionFilter.Operation.MATCHES, 2, 3, 0, 0);
        spans.setTest(true); // for test, all documents are 5 long

        Spans exp = new MockSpans(
                new int[] { 1, 2 },
                new int[] { 1, 2 },
                new int[] { 4, 4 });
        TestUtil.assertEquals(exp, spans);
    }

}
