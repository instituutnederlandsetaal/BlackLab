package nl.inl.blacklab.server.lib.requests;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.queryParser.corpusql.BcqlQueryLanguageParser;
import nl.inl.blacklab.resultproperty.HitGroupPropertyScore;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.BlsMain;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.QueryParamsMap;
import nl.inl.blacklab.webservice.WsParam;

public class TestCollocationRequest {

    private static MockBlackLabIndex index;

    private static AnnotatedField field;

    private static File tmpDir;

    @BeforeClass
    public static void beforeClass() {
        BLSConfig blsConfig = new BLSConfig();
        tmpDir = new File(System.getProperty("java.io.tmpdir"), "blacklab-tmp");
        if (!tmpDir.mkdirs())
            throw new IllegalStateException("Unable to create tmp directory: " + tmpDir.getAbsolutePath());
        blsConfig.setIndexLocations(List.of(tmpDir.getAbsolutePath())); // prevent error
        BlsMain.create(blsConfig);
        index = new MockBlackLabIndex();
        field = index.mainAnnotatedField();
    }

    @AfterClass
    public static void afterClass() {
        index = null;
        field = null;
        if (!tmpDir.delete())
            throw new IllegalStateException("Unable to delete tmp directory: " + tmpDir.getAbsolutePath());
        tmpDir = null;
    }

    private static final QueryParams.IndexResolver testResolver = corpusName -> {
        if (corpusName.equals("test"))
            return index;
        throw new BadRequest("UNKNOWN_CORPUS", "Unknown corpus: " + corpusName);
    };

    @Test
    public void testRelationScorerDirection() {
        for (String type: new String[] { "relsources", "reltargets" }) {

            // Ensure that countHits call will not fail if the expected query is used.
            String query = switch (type) {
                case "relsources" -> "_ -obj-> \"eat\"";
                case "reltargets" -> "rspan(\"eat\" -obj-> _, str('target'))";
                default -> throw new IllegalStateException();
            };
            setUpCountHitsResponse(query);

            RequestHits reqHits = RequestHits.fromParamsCollocations(params(Map.of(WsParam.COLLOCATION_TYPE, type,
                    WsParam.RELATION_TYPE, "obj")), false);

            // SonarCloud requires an assert, even though we already know the query is correct if no exception is thrown
            // (i.e. the countHits() in the Mock index responds because we set it up with the correct query)
            TextPattern tp = BcqlQueryLanguageParser.parseQuery(query);
            Assert.assertEquals(tp, reqHits.patternOriginal());
        }
    }

    @Test
    public void testViewGroupPreservesRequest() {
        for (String type: new String[] { "proximity", "relsources", "reltargets" }) {
            QueryParams params = params(Map.of(WsParam.COLLOCATION_TYPE, type,
                    WsParam.CONTEXT, "2:3", WsParam.SAMPLE_NUMBER, "12", WsParam.SAMPLE_SEED, "7"));

            // Ensure that countHits call will not fail if the expected query is used.
            setUpCountHitsResponse(switch (type) {
                case "proximity" -> "\"eat\"";
                case "relsources" -> "_ --> \"eat\"";
                case "reltargets" -> "rspan(\"eat\" --> _, str('target'))";
                default -> throw new IllegalStateException();
            });

            RequestHits grouped = RequestHits.fromParamsCollocations(params, false);

            RequestHits group = RequestHits.fromParamsCollocations(params.withOverrides(Map.of(
                    WsParam.VIEW_GROUP, "cws:word:i:apple", WsParam.SORT_BY, "-hitposition",
                    WsParam.FIRST_RESULT, 3L, WsParam.NUMBER_OF_RESULTS, 2L)), false);
            Assert.assertEquals(grouped.pattern(), group.pattern());
            Assert.assertEquals(grouped.groupBy(), group.groupBy());
            Assert.assertEquals("hit:contents%word:i", group.groupBy().serialize());
            Assert.assertEquals(grouped.sampleParams(), group.sampleParams());
            Assert.assertEquals(grouped.contextSettings(), group.contextSettings());
            Assert.assertEquals("cws:word:i:apple", group.viewGroup());
            Assert.assertEquals("-hitposition", group.sortBy().serialize());
            Assert.assertEquals(new WindowSettings(3, 2), group.windowSettings());
            Assert.assertEquals(HitGroupPropertyScore.get(), grouped.sortGroupsBy());
            Assert.assertNull(grouped.sortBy());

            RequestHits count = RequestHits.fromParamsCollocations(params.withOverrides(Map.of(
                    WsParam.VIEW_GROUP, "cws:word:i:apple", WsParam.FIRST_RESULT, 0L,
                    WsParam.NUMBER_OF_RESULTS, 0L, WsParam.SORT_BY, "score")), false);
            Assert.assertEquals(new WindowSettings(0, 0), count.windowSettings());
            Assert.assertNull(count.sortBy());
        }
    }

    @Test
    public void testCollocationGroupingOverridesExplicitGroup() {
        // Ensure that countHits call will not fail if the expected query is used.
        setUpCountHitsResponse("\"eat\"");

        RequestHits request = RequestHits.fromParamsCollocations(params(Map.of(
                WsParam.GROUP_BY, "docid", WsParam.ANNOTATION, "lemma", WsParam.SENSITIVE, "true",
                WsParam.SORT_BY, "-identity")), false);
        Assert.assertEquals("hit:contents%lemma:s", request.groupBy().serialize());
        Assert.assertEquals("-identity", request.sortGroupsBy().serialize());
        Assert.assertNull(request.sortBy());
    }

    private static void setUpCountHitsResponse(String query) {
        TextPattern pattEat = index.getQueryParser("bcql").parse(query).pattern();
        index.clearCountHitsResponses();
        index.putCountHitsResponse(field, new CompleteQuery(pattEat, null), 1234L);
    }

    @Test
    public void testInvalidParametersUseCodedBadRequests() {
        assertBadRequest("INVALID_COLLOCATION_TYPE", () -> RequestHits.fromParamsCollocations(
                params(Map.of(WsParam.COLLOCATION_TYPE, "invalid")), false));
        assertBadRequest("INVALID_CONTEXT", () -> RequestHits.fromParamsCollocations(
                params(Map.of(WsParam.CONTEXT, "not:a:context")), false));
        assertBadRequest("INVALID_SCORER", () -> RequestHits.fromParamsCollocations(
                params(Map.of(WsParam.SCORER_TYPE, "missing")), false));
        assertBadRequest("PATT_SYNTAX_ERROR", () -> RequestHits.fromParamsCollocations(
                params(Map.of(WsParam.PATTERN, "\"unterminated")), false));
        assertBadRequest("PATT_SYNTAX_ERROR", () -> RequestHits.fromParamsCollocations(
                params(Map.of(WsParam.PATTERN, "[missing=\"word\"]")), false));
        assertBadRequest("UNKNOWN_ANNOTATION", () -> RequestHits.fromParamsCollocations(
                params(Map.of(WsParam.ANNOTATION, "missing")), false));
    }

    private static void assertBadRequest(String errorCode, Runnable runnable) {
        BadRequest exception = Assert.assertThrows(BadRequest.class, runnable::run);
        Assert.assertEquals(errorCode, exception.getBlsErrorCode());
    }

    static QueryParams params(Map<WsParam, String> values) {
        Map<WsParam, String> params = new LinkedHashMap<>();
        params.put(WsParam.OPERATION, "collocations");
        params.put(WsParam.FIELD, "contents");
        params.put(WsParam.PATTERN, "\"eat\"");
        params.putAll(values);
        QueryParams.CorpusRefByName corpusRef = new QueryParams.CorpusRefByName("test", testResolver);
        return new QueryParamsMap(corpusRef, params, null, null, new BLSConfig(), false);
    }
}
