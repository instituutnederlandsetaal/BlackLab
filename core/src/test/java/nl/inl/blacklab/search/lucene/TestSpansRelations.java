package nl.inl.blacklab.search.lucene;

import java.util.concurrent.Callable;

import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

public class TestSpansRelations {

    private SpansRelations tagRelationQuery(BLSpans a, boolean hasPrimaryValueIndicators) {
        return new SpansRelations("contents", "test", a,
                hasPrimaryValueIndicators, SpanQueryRelations.Direction.FORWARD,
                RelationInfo.SpanMode.FULL_SPAN, "", null);
    }

    private void testRelationsAndAdjust(int[] aDoc, int[] aStart, int[] aEnd, Callable<BLSpans> createSpans) throws Exception {
        testRelationsAndAdjust(aDoc, aStart, aEnd, createSpans, false);
    }

    private void testRelationsAndAdjust(int[] aDoc, int[] aStart, int[] aEnd, Callable<BLSpans> createSpans, boolean skipFirstNextDoc) throws Exception {
        // Test without adjustment
        BLSpans full = new MockSpans(aDoc, aStart, aEnd);
        TestUtil.assertEquals(full, createSpans.call(), skipFirstNextDoc);

        // Test getting full spans
        full = new MockSpans(aDoc, aStart, aEnd);
        TestUtil.assertEquals(full, new SpansRelationSpanAdjust(
                createSpans.call(), RelationInfo.SpanMode.FULL_SPAN, null), skipFirstNextDoc);

        // Test getting source spans
        BLSpans sources = new MockSpans(aDoc, aStart, aStart);
        TestUtil.assertEquals(sources, new SpansRelationSpanAdjust(
                createSpans.call(), RelationInfo.SpanMode.SOURCE, null), skipFirstNextDoc);

        // Test getting target spans
        BLSpans targets = new MockSpans(aDoc, aEnd, aEnd);
        TestUtil.assertEquals(targets, new SpansRelationSpanAdjust(
                createSpans.call(), RelationInfo.SpanMode.TARGET, null), skipFirstNextDoc);
    }

    @Test
    public void test() throws Exception {
        int[] aDoc   = {  1, 2, 2 };
        int[] aStart = { 10, 1, 4 };
        int[] aEnd   = { 21, 2, 6 };
        testRelationsAndAdjust(aDoc, aStart, aEnd, () -> {
            BLSpans a = MockSpans.withRelationInfoInPayload(aDoc, aStart, aEnd, null);
            return tagRelationQuery(a, false);
        });
    }

    @Test
    public void testWithIsPrimary() throws Exception {
        int[] aDoc   = {  1, 2, 2 };
        int[] aStart = { 10, 1, 4 };
        int[] aEnd   = { 21, 2, 6 };
        boolean[] aIsPrimary = { true, false, true };

        testRelationsAndAdjust(aDoc, aStart, aEnd, () -> {
            BLSpans a = MockSpans.withRelationInfoInPayload(aDoc, aStart, aEnd, aIsPrimary);
            return tagRelationQuery(a, true);
        });
    }

    @Test
    public void testNested() throws Exception {
        int[] aDoc = { 1, 1 };
        int[] aStart = { 2, 4 };
        int[] aEnd = { 7, 5 };
        testRelationsAndAdjust(aDoc, aStart, aEnd, () -> {
            BLSpans a = MockSpans.withRelationInfoInPayload(aDoc, aStart, aEnd, null);
            return tagRelationQuery(a, false);
        });
    }

    /**
     * Test the case where there's an empty tag between two tokens.
     *
     * E.g.: <code>quick &lt;b&gt;&lt;/b&gt; brown</code>
     *
     */
    @Test
    public void testEmptyTag() throws Exception {
        int[] aDoc = { 1, 1 };
        int[] aStart = { 2, 4 };
        int[] aEnd = { 2, 7 };
        testRelationsAndAdjust(aDoc, aStart, aEnd, () -> {
            BLSpans a = MockSpans.withRelationInfoInPayload(aDoc, aStart, aEnd, null);
            return tagRelationQuery(a, false);
        });
    }

    @Test
    public void testSkip() throws Exception {
        // Actual
        int[] aDoc   = { 1, 1,  2,  2 };
        int[] aStart = { 2, 4, 12, 14 };
        int[] aEnd   = { 5, 7, 17, 15 };
        // Expected
        int[] expDoc   = {  2, 2 };
        int[] expStart = { 12, 14 };
        int[] expEnd   = { 17, 15 };
        testRelationsAndAdjust(expDoc, expStart, expEnd, () -> {
            BLSpans a = MockSpans.withRelationInfoInPayload(aDoc, aStart, aEnd, null);
            BLSpans spans = tagRelationQuery(a, false);
            spans.advance(2); // skip to doc 2
            return spans;
        }, true);
    }
}
