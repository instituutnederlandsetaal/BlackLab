package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockSpans;

public class TestSpansCaptureRelations {

    private final String ANNOTATED_FIELD_NAME = "contents";

    private BLSpans tags(int[] docs, int[] starts, int[] ends) {
        return new MockSpans(docs, starts, ends, SpanGuarantees.SORTED) {
            @Override
            public RelationInfo getRelationInfo() {
                return RelationInfo.createTag(startPosition(), endPosition(), "s", ANNOTATED_FIELD_NAME);
            }
        };
    }

    private SpansContext initialize(BLSpans spans) {
        SpansContext context = new SpansContext(ANNOTATED_FIELD_NAME);
        spans.setSpansContext(context);
        return context;
    }

    private void assertCapture(BLSpans spans, SpansContext context, int start, int end) {
        MatchInfo[] matchInfo = new MatchInfo[context.numberOfMatchInfos()];
        spans.getMatchInfo(matchInfo);
        RelationInfo relationInfo = RelationInfo.createTag(start, end, "s", ANNOTATED_FIELD_NAME);
        RelationListInfo relationListInfo = RelationListInfo.create(List.of(relationInfo), ANNOTATED_FIELD_NAME);
        Assert.assertEquals(relationListInfo, matchInfo[matchInfo.length - 1]);
        MatchInfo[] repeated = new MatchInfo[matchInfo.length];
        spans.getMatchInfo(repeated);
        Assert.assertArrayEquals(matchInfo, repeated);
    }

    @Test
    public void testOverlappingHits() throws IOException {
        BLSpans hits = new MockSpans(
                new int[] { 1, 1, 1, 2, 3 },
                new int[] { 0, 1, 2, 0, 0 },
                new int[] { 10, 4, 6, 10, 10 });
        BLSpans relations = tags(
                new int[] { 1, 1, 3 },
                new int[] { 2, 8, 1 },
                new int[] { 3, 12, 2 });
        BLSpans spans = new SpansCaptureRelationsWithinSpan(hits, relations, null, "rels");
        SpansContext context = initialize(spans);
        Assert.assertEquals(1, spans.nextDoc());
        // The three hits in doc 1 all overlap with the relation 2-3 in doc 1, so all three should capture it.
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(i, spans.nextStartPosition());
            assertCapture(spans, context, 2, 3);
        }
        Assert.assertEquals(2, spans.nextDoc());
        Assert.assertEquals(0, spans.nextStartPosition());
        MatchInfo[] matchInfo = new MatchInfo[context.numberOfMatchInfos()];
        spans.getMatchInfo(matchInfo);
        Assert.assertEquals(RelationListInfo.create(List.of(), ANNOTATED_FIELD_NAME), matchInfo[0]);
        Assert.assertEquals(3, spans.nextDoc());
        Assert.assertEquals(0, spans.nextStartPosition());
        assertCapture(spans, context, 1, 2);
        Assert.assertEquals(BLSpans.NO_MORE_DOCS, spans.nextDoc());
    }

    @Test
    public void testNamedCaptureOutOfOrder() throws IOException {
        // First hit will capture 2-5 for "span", second hit wil capture 0-5.
        // This affects SpansCaptureRelationsWithinSpan (relation 1-2 overlaps with 0-5 but not with 2-5,
        // so we expect to capture the relation for the second hit only).
        BLSpans hits = new MockSpans(
                new int[] { 1, 1 },
                new int[] { 2, 3 },
                new int[] { 3, 4 }) {
            private int captureIndex;

            @Override
            public void passSpansContextToClauses(SpansContext context) {
                captureIndex = context.registerMatchInfo("span", MatchInfo.Type.SPAN);
            }

            @Override
            public void getMatchInfo(MatchInfo[] matchInfo) {
                matchInfo[captureIndex] = SpanInfo.create(startPosition() == 2 ? 2 : 0, 5, ANNOTATED_FIELD_NAME);
            }

            @Override
            public boolean hasMatchInfo() {
                return true;
            }
        };
        BLSpans spans = new SpansCaptureRelationsWithinSpan(hits,
                tags(new int[] { 1 }, new int[] { 1 }, new int[] { 2 }), "span", "rels");
        SpansContext context = initialize(spans);
        Assert.assertEquals(1, spans.nextDoc());
        Assert.assertEquals(2, spans.nextStartPosition());
        MatchInfo[] matchInfo = new MatchInfo[context.numberOfMatchInfos()];
        spans.getMatchInfo(matchInfo);
        Assert.assertEquals(RelationListInfo.create(List.of(), ANNOTATED_FIELD_NAME), matchInfo[1]);
        Assert.assertEquals(3, spans.nextStartPosition());
        assertCapture(spans, context, 1, 2);
    }

    @Test
    public void testRepeatedSentenceContext() throws IOException {
        // First and second hit both capture the same sentence context.
        // Test that this works, and doesn't skip the second hit because the sentence was already captured before.
        BLSpans hits = new MockSpans(
                new int[] { 1, 1, 1 },
                new int[] { 1, 3, 6 },
                new int[] { 2, 4, 7 });
        int[] docs   = { 1, 1, 1 };
        int[] starts = { 0, 5, 10 };
        int[] ends   = { 5, 10, 15 };
        BLSpans spans = new SpansWithinShortestRepetition(hits, tags(docs, starts, ends),
                tags(docs, starts, ends), "context_rels", 0, 0);
        SpansContext context = initialize(spans);
        Assert.assertEquals(1, spans.nextDoc());
        Assert.assertEquals(1, spans.nextStartPosition());
        assertCapture(spans, context, 0, 5);
        Assert.assertEquals(3, spans.nextStartPosition());
        assertCapture(spans, context, 0, 5);
        Assert.assertEquals(6, spans.nextStartPosition());
        assertCapture(spans, context, 5, 10);
        Assert.assertEquals(BLSpans.NO_MORE_POSITIONS, spans.nextStartPosition());
    }

    @Test
    public void testCrossFieldRelation() throws IOException {
        // Cross-field relation with source 0-20
        RelationInfo relation = RelationInfo.createWithFields(ANNOTATED_FIELD_NAME, "contents__nl");
        relation.fill(0, false, 0, 20, 30, 40, false);
        relation.setFullRelationType("al__nl::sentence", false);
        BLSpans relations = new MockSpans(new int[] { 1 }, new int[] { 0 }, new int[] { 20 }) {
            @Override
            public RelationInfo getRelationInfo() {
                return relation;
            }
        };
        // The hits are in the "contents" field, but the relation target is in the "contents__nl" field.
        // The relation should still be captured by both hits because the source overlaps the hits.
        BLSpans hits = new MockSpans(
                new int[] { 1, 1 },
                new int[] { 0, 0 },
                new int[] { 25, 26 });
        BLSpans spans = new SpansCaptureRelationsWithinSpan(hits, relations, null, "rels");
        SpansContext context = initialize(spans);
        Assert.assertEquals(1, spans.nextDoc());
        for (int i = 0; i < 2; i++) {
            Assert.assertEquals(0, spans.nextStartPosition());
            MatchInfo[] matchInfo = new MatchInfo[context.numberOfMatchInfos()];
            spans.getMatchInfo(matchInfo);
            Assert.assertEquals(RelationListInfo.create(List.of(relation), ANNOTATED_FIELD_NAME), matchInfo[0]);
        }
    }
}
