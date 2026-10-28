package nl.inl.blacklab.server.lib.requests;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.queryParser.corpusql.BcqlQueryLanguageParser;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.HitGroupCollocationScorer.CollocationType;
import nl.inl.blacklab.server.exceptions.BadRequest;

public class TestCollocationQuery {

    @Test
    public void testRelationDirections() {
        assertQuery("rspan(\"eat\" -(obj)-> [pos=\"N\"], \"target\")", ContextSize.ZERO,
                CollocationType.RELATION_TARGETS, "");
        assertQuery("[pos=\"N\"] -(obj)-> \"eat\"", ContextSize.ZERO,
                CollocationType.RELATION_SOURCES, "");
    }

    @Test
    public void testProximityAndTagContext() {
        assertQuery("meet([pos=\"N\"], \"eat\", -1, 1)", ContextSize.get(0, 0, 100),
                CollocationType.PROXIMITY, "");
        assertQuery("meet([pos=\"N\"], \"eat\", -3, 4)", ContextSize.get(3, 4, 100),
                CollocationType.PROXIMITY, "");
        assertQuery("meet_within([pos=\"N\"], \"eat\", <s/>, -3, 4)", ContextSize.get(3, 4, 100),
                CollocationType.PROXIMITY, "<s/>");
        assertQuery("meet_within([pos=\"N\"], \"eat\", <s/>)", ContextSize.ZERO,
                CollocationType.PROXIMITY, "<s/>");
        assertQuery("meet_within([pos=\"N\"], \"eat\", <s/>)", ContextSize.fromContextDef("s", 100),
                CollocationType.PROXIMITY, "");
    }

    @Test
    public void testConflictingWithin() {
        assertBadRequest("INVALID_CONTEXT", () -> query(ContextSize.fromContextDef("s", 100),
                CollocationType.PROXIMITY, "<p/>"));
        assertBadRequest("INVALID_CONTEXT", () -> query(ContextSize.ZERO,
                CollocationType.RELATION_TARGETS, "<s/>"));
    }

    private static void assertBadRequest(String errorCode, Runnable runnable) {
        BadRequest exception = Assert.assertThrows(BadRequest.class, runnable::run);
        Assert.assertEquals(errorCode, exception.getBlsErrorCode());
    }

    private static void assertQuery(String expected, ContextSize context, CollocationType type, String within) {
        Assert.assertEquals(BcqlQueryLanguageParser.parseQuery(expected),
                BcqlQueryLanguageParser.parseQuery(query(context, type, within)));
    }

    private static String query(ContextSize context, CollocationType type, String within) {
        return RequestHits.getCollocationQuery(context, "\"eat\"", "[pos=\"N\"]", type, "obj", within);
    }
}
