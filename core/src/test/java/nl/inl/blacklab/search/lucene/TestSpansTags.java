package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.queries.spans.Spans;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

public class TestSpansTags {

    @Test
    public void test() throws IOException {
        int[] aDoc = { 1, 2, 2 };
        int[] aStart = { 10, 1, 4 };
        int[] aEnd = { 21, 2, 6 };
        BLSpans a = MockSpans.withEndInPayload(aDoc, aStart, aEnd, null);
        Spans spans = new SpansTagsExternal(a, false);

        Spans exp = new MockSpans(aDoc, aStart, aEnd);
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testWithIsPrimary() throws IOException {
        int[] aDoc = { 1, 2, 2 };
        int[] aStart = { 10, 1, 4 };
        int[] aEnd = { 21, 2, 6 };
        boolean[] aIsPrimary = { true, false, true };
        BLSpans a = MockSpans.withEndInPayload(aDoc, aStart, aEnd, aIsPrimary);
        Spans spans = new SpansTagsExternal(a, true);

        Spans exp = new MockSpans(aDoc, aStart, aEnd);
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testNested() throws IOException {
        int[] aDoc = { 1, 1 };
        int[] aStart = { 2, 4 };
        int[] aEnd = { 7, 5 };
        BLSpans a = MockSpans.withEndInPayload(aDoc, aStart, aEnd, null);

        Spans spans = new SpansTagsExternal(a, false);

        Spans exp = new MockSpans(aDoc, aStart, aEnd);
        TestUtil.assertEquals(exp, spans);
    }

    /**
     * Test the case where there's an empty tag between two tokens.
     * <p>
     * E.g.: <code>quick &lt;b&gt;&lt;/b&gt; brown</code>
     *
     */
    @Test
    public void testEmptyTag() throws IOException {
        int[] aDoc = { 1, 1 };
        int[] aStart = { 2, 4 };
        int[] aEnd = { 2, 7 };
        BLSpans a = MockSpans.withEndInPayload(aDoc, aStart, aEnd, null);

        Spans spans = new SpansTagsExternal(a, false);

        Spans exp = new MockSpans(aDoc, aStart, aEnd);
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testSkip() throws IOException {
        int[] aDoc = { 1, 1, 2, 2 };
        int[] aStart = { 2, 4, 12, 14 };
        int[] aEnd = { 5, 7, 17, 15 };
        BLSpans a = MockSpans.withEndInPayload(aDoc, aStart, aEnd, null);

        Spans spans = new SpansTagsExternal(a, false);
        spans.advance(2);

        int[] expDoc = { 2, 2 };
        int[] expStart = { 12, 14 };
        int[] expEnd = { 17, 15 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans, true);
    }
}
