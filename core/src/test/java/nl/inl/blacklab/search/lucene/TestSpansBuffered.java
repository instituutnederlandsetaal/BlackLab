package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.Spans;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockSpans;

public class TestSpansBuffered {

    int[] aDoc   = { 1, 1, 1, 2 };
    int[] aStart = { 2, 3, 3, 1 };
    int[] aEnd   = { 4, 5, 6, 2 };

    BLSpans a = new MockSpans(aDoc, aStart, aEnd, SpanGuarantees.SORTED);

    @Test
    public void test() throws IOException {
        SpansBuffered spans = new SpansBuffered(a);
        spans.setHitQueryContext(null); // create buffer
        Assert.assertEquals(1, spans.nextDoc());
        checkDoc1Hits(spans);
        spans.reset();
        checkDoc1Hits(spans);
        spans.reset();
        Assert.assertEquals(2, spans.nextStartPosition());
        spans.reset();
        spans.mark();
        spans.reset();
        Assert.assertEquals(2, spans.nextStartPosition());
        spans.mark();
        Assert.assertEquals(3, spans.nextStartPosition());
        Assert.assertEquals(3, spans.nextStartPosition());
        spans.reset();
        Assert.assertEquals(3, spans.nextStartPosition());
        Assert.assertEquals(3, spans.nextStartPosition());

        Assert.assertEquals(2, spans.nextDoc());
        spans.reset();
        Assert.assertEquals(1, spans.nextStartPosition());
        spans.reset();
        Assert.assertEquals(1, spans.nextStartPosition());
        spans.mark();
        spans.reset();
        Assert.assertEquals(Spans.NO_MORE_POSITIONS, spans.nextStartPosition());
        spans.reset();
        Assert.assertEquals(Spans.NO_MORE_POSITIONS, spans.nextStartPosition());
    }

    private static void checkDoc1Hits(SpansBuffered spans) throws IOException {
        Assert.assertEquals(2, spans.nextStartPosition());
        Assert.assertEquals(3, spans.nextStartPosition());
        Assert.assertEquals(3, spans.nextStartPosition());
    }
}
