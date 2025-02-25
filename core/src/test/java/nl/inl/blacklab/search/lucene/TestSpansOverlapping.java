package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.queries.spans.Spans;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

public class TestSpansOverlapping {

    @Test
    public void test() throws IOException {
        int[] aDoc   = { 1, 1, 1 };
        int[] aStart = { 1, 1, 2 };
        int[] aEnd   = { 2, 3, 4 };
        BLSpans a = new MockSpans(aDoc, aStart, aEnd);

        int[] bDoc   = { 1, 1, 1 };
        int[] bStart = { 2, 2, 3 };
        int[] bEnd   = { 4, 5, 4 };
        BLSpans b = new MockSpans(bDoc, bStart, bEnd);

        Spans spans = new SpansOverlapping(a, b, SpansOverlapping.OVERLAP);

        int[] expDoc   = { 1, 1, 1, 1, 1 };
        int[] expStart = { 2, 2, 2, 2, 3 };
        int[] expEnd   = { 3, 3, 4, 4, 4 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }
}
