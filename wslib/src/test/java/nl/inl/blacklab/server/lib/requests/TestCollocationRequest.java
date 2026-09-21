package nl.inl.blacklab.server.lib.requests;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import nl.inl.blacklab.queryParser.corpusql.BcqlQueryLanguageParser;
import nl.inl.blacklab.resultproperty.HitGroupPropertyScore;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternDefaultValue;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.QueryParamsMap;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.ResultHits;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WsParam;

public class TestCollocationRequest {

    @Test
    public void testRelationScorerDirection() {
        for (String type: new String[] { "relsources", "reltargets" }) {
            try (Fixture fixture = new Fixture()) {
                RequestHits.fromParamsCollocations(fixture.params(Map.of(WsParam.COLLOCATION_TYPE, type,
                        WsParam.RELATION_TYPE, "obj")), false);
                ArgumentCaptor<CompleteQuery> query = ArgumentCaptor.forClass(CompleteQuery.class);
                Mockito.verify(fixture.index).countHits(Mockito.eq(fixture.field), query.capture());
                Assert.assertSame(fixture.filter, query.getValue().filter());
                TextPatternRelationMatch relation = (TextPatternRelationMatch)query.getValue().pattern();
                TextPattern keyword = BcqlQueryLanguageParser.parseQuery("\"eat\"");
                Assert.assertEquals(type.equals("relsources") ? TextPatternDefaultValue.get() : keyword,
                        relation.getParent());
                Assert.assertEquals(type.equals("relsources") ? keyword : TextPatternDefaultValue.get(),
                        relation.getChildren().get(0).getTarget());
            }
        }
    }

    @Test
    public void testViewGroupPreservesRequest() {
        for (String type: new String[] { "proximity", "relsources", "reltargets" }) {
            try (Fixture fixture = new Fixture()) {
                QueryParams params = fixture.params(Map.of(WsParam.COLLOCATION_TYPE, type,
                        WsParam.CONTEXT, "2:3", WsParam.SAMPLE_NUMBER, "12", WsParam.SAMPLE_SEED, "7"));
                RequestHits grouped = RequestHits.fromParamsCollocations(params, false);
                RequestHits group = RequestHits.fromParamsCollocations(params.withOverrides(Map.of(
                        WsParam.VIEW_GROUP, "cws:word:i:apple", WsParam.SORT_BY, "-hitposition",
                        WsParam.FIRST_RESULT, 3L, WsParam.NUMBER_OF_RESULTS, 2L)), false);
                Assert.assertEquals(grouped.pattern(), group.pattern());
                Assert.assertEquals(grouped.groupBy(), group.groupBy());
                Assert.assertEquals("hit:word:i", group.groupBy().serialize());
                Assert.assertSame(fixture.filter, group.filterQuery());
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

                try (MockedStatic<WebserviceOperations> operations = Mockito.mockStatic(WebserviceOperations.class)) {
                    ResultHits result = Mockito.mock(ResultHits.class);
                    operations.when(() -> WebserviceOperations.hits(group)).thenReturn(result);
                    ResponseStreamer response = Mockito.mock(ResponseStreamer.class);
                    WebserviceRequestHandler.opHits(group, response, false);
                    Mockito.verify(response).hitsResponse(result, false);
                }
            }
        }
    }

    @Test
    public void testCollocationGroupingOverridesExplicitGroup() {
        try (Fixture fixture = new Fixture()) {
            RequestHits request = RequestHits.fromParamsCollocations(fixture.params(Map.of(
                    WsParam.GROUP_BY, "docid", WsParam.ANNOTATION, "lemma", WsParam.SENSITIVE, "true",
                    WsParam.SORT_BY, "-identity")), false);
            Assert.assertEquals("hit:lemma:s", request.groupBy().serialize());
            Assert.assertEquals("-identity", request.sortGroupsBy().serialize());
            Assert.assertNull(request.sortBy());
        }
    }

    @Test
    public void testInvalidParametersUseCodedBadRequests() {
        try (Fixture fixture = new Fixture()) {
            assertBadRequest("INVALID_COLLOCATION_TYPE", () -> RequestHits.fromParamsCollocations(
                    fixture.params(Map.of(WsParam.COLLOCATION_TYPE, "invalid")), false));
            assertBadRequest("INVALID_CONTEXT", () -> RequestHits.fromParamsCollocations(
                    fixture.params(Map.of(WsParam.CONTEXT, "not:a:context")), false));
            assertBadRequest("INVALID_SCORER", () -> RequestHits.fromParamsCollocations(
                    fixture.params(Map.of(WsParam.SCORER_TYPE, "missing")), false));
            assertBadRequest("PATT_SYNTAX_ERROR", () -> RequestHits.fromParamsCollocations(
                    fixture.params(Map.of(WsParam.PATTERN, "\"unterminated")), false));

            Mockito.when(fixture.field.annotation("missing")).thenReturn(null);
            assertBadRequest("PATT_SYNTAX_ERROR", () -> RequestHits.fromParamsCollocations(
                    fixture.params(Map.of(WsParam.PATTERN, "[missing=\"word\"]")), false));
            assertBadRequest("UNKNOWN_ANNOTATION", () -> RequestHits.fromParamsCollocations(
                    fixture.params(Map.of(WsParam.ANNOTATION, "missing")), false));
        }
    }

    private static void assertBadRequest(String errorCode, Runnable runnable) {
        BadRequest exception = Assert.assertThrows(BadRequest.class, runnable::run);
        Assert.assertEquals(errorCode, exception.getBlsErrorCode());
    }

    private static class Fixture implements AutoCloseable {
        final BlackLabIndex index = Mockito.mock(BlackLabIndex.class, Mockito.RETURNS_DEEP_STUBS);
        final AnnotatedField field = index.mainAnnotatedField();
        final Query filter = new MatchAllDocsQuery();
        final MockedStatic<ParamUtil> util;

        Fixture() {
            Mockito.when(index.annotatedField("contents")).thenReturn(field);
            Mockito.when(index.getQueryParser("bcql")).thenReturn(new BcqlQueryLanguageParser(index, null));
            Mockito.when(field.index()).thenReturn(index);
            for (String name: new String[] { "word", "lemma" }) {
                Annotation annotation = field.annotation(name);
                Mockito.when(annotation.name()).thenReturn(name);
                Mockito.when(annotation.fieldAndAnnotationName()).thenReturn(name);
                Mockito.when(annotation.hasSensitivity(MatchSensitivity.INSENSITIVE)).thenReturn(true);
                Mockito.when(annotation.hasSensitivity(MatchSensitivity.SENSITIVE)).thenReturn(true);
            }
            Annotation word = field.annotation("word");
            Mockito.when(field.mainAnnotation()).thenReturn(word);
            util = Mockito.mockStatic(ParamUtil.class, invocation -> invocation.getMethod().getName().equals("index") ?
                    index : invocation.callRealMethod());
        }

        QueryParams params(Map<WsParam, String> values) {
            Map<WsParam, String> params = new LinkedHashMap<>();
            params.put(WsParam.OPERATION, "collocations");
            params.put(WsParam.FIELD, "contents");
            params.put(WsParam.PATTERN, "\"eat\"");
            params.putAll(values);
            return new QueryParamsMap("test", params, null, filter, new BLSConfig(), false);
        }

        @Override
        public void close() {
            util.close();
        }
    }
}
