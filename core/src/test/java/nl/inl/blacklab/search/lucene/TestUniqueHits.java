package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.Spans;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

public class TestUniqueHits {

    int[] aDoc   = {  1,  1,  2, 3, 3 };
    int[] aStart = { 10, 10, 10, 1, 1 };
    int[] aEnd   = { 11, 11, 11, 2, 2 };

    @Test
    public void test() throws IOException {
        BLSpans a = new MockSpans(aDoc, aStart, aEnd, SpanGuarantees.SORTED);

        Spans spans = new SpansUnique(a);

        int[] expDoc   = {  1,  2, 3 };
        int[] expStart = { 10, 10, 1 };
        int[] expEnd   = { 11, 11, 2 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }

    /** Match info for above hits. Notice that the first three are identical, but the last two are not -
     *  we therefore expect the last two hits to both be returned, and not reduced to 1 using SpansUnique.
     */
    RelationInfo[] aRelationInfo = {
            RelationInfo.create(false, 10, 10, 11, 11, 0, "abc"),
            RelationInfo.create(false, 10, 10, 11, 11, 0, "abc"),
            RelationInfo.create(false, 10, 10, 11, 11, 0, "abc"),
            RelationInfo.create(false, 1, 1, 2, 2, 0, "abc"),
            RelationInfo.create(false, 1, 1, 1, 2, 0, "abc"),
    };

    @Test
    public void testWithMatchInfoSpansUnique() throws IOException {
        BLSpans a = MockSpans.withRelationInfoObjectsInPayload(aDoc, aStart, aEnd, aRelationInfo);
        BLSpans tags = new SpansRelations("contents", "test", a,
                false, SpanQueryRelations.Direction.FORWARD,
                RelationInfo.SpanMode.FULL_SPAN, "abc", null);
        BLSpans spans = new SpansUnique(tags);
        HitQueryContext context = new HitQueryContext(null, "contents");
        context.registerMatchInfo("abc", MatchInfo.Type.RELATION);
        spans.setHitQueryContext(context);

        int[] expDoc   = {  1,  2, 3, 3 };
        int[] expStart = { 10, 10, 1, 1 };
        int[] expEnd   = { 11, 11, 2, 2 }; // we expect last hit twice because match info is different!
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testWithMatchInfoPerDocSortedSpans() throws IOException {
        MockSpans a = MockSpans.withRelationInfoObjectsInPayload(aDoc, aStart, aEnd, aRelationInfo);
        a.setGuarantees(SpanGuarantees.NONE); // so PerDocumentSortedSpans doesn't complain we're already sorted
        BLSpans tags = new SpansRelations("contents", "test", a,
                false, SpanQueryRelations.Direction.FORWARD,
                RelationInfo.SpanMode.FULL_SPAN, "abc", null);
        BLSpans spans = new PerDocumentSortedSpans(tags, true, true);
        HitQueryContext context = new HitQueryContext(null, "contents");
        context.registerMatchInfo("abc", MatchInfo.Type.RELATION);
        spans.setHitQueryContext(context);

        int[] expDoc   = {  1,  2, 3, 3 };
        int[] expStart = { 10, 10, 1, 1 };
        int[] expEnd   = { 11, 11, 2, 2 }; // we expect last hit twice because match info is different!
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }
}
