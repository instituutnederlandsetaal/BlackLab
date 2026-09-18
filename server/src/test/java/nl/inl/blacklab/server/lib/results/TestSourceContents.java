package nl.inl.blacklab.server.lib.results;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.junit.Test;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.JsonNode;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.exceptions.UnsupportedConcordanceRepresentation;
import nl.inl.blacklab.index.BLIndexObjectFactoryLucene;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.BLInputDocumentLucene;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.SourceRangeEncoding;
import nl.inl.blacklab.search.indexmetadata.SourceUnitCodec;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.lucene.SpanInfo;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamAbstract;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.requests.RequestDocContents;
import nl.inl.blacklab.server.lib.requests.RequestDocSnippet;
import nl.inl.blacklab.server.lib.requests.RequestHits;
import nl.inl.util.Json;
import nl.inl.util.XmlHighlighter;

public class TestSourceContents {
    private static final String FIELD = "contents__nl";
    private static final String XML = "<root xmlns:bl=\"urn:input\"><w>A</w><w>B</w></root>";
    private static final String TEXT = "<raw>& text";

    @Test
    public void testCompleteContainerContents() throws Exception {
        try (Fixture f = new Fixture(true)) {
            ResultDocContents partial = f.result(0, 0, 1, null);
            assertEquals(XML, partial.getContent());
            org.w3c.dom.Document xml = parseXml(render(partial, DataFormat.XML));
            assertEquals("blacklabResponse", xml.getDocumentElement().getNodeName());
            assertEquals("urn:input", xml.getDocumentElement().lookupNamespaceURI("bl"));
            assertEquals("AB", xml.getDocumentElement().getTextContent());
            assertEquals(DataStream.XML_PROLOG + XML, render(f.result(0, -1, -1, null), DataFormat.XML));
            assertEquals("<empty/>", f.result(2, 0, 0, null).getContent());
            assertEquals(XML, f.result(0, 1, 1, null).getContent());
            assertEquals(XML, f.result(0, 2, 2, null).getContent());
            assertThrows(BadRequest.class, () -> f.result(0, 0, 3, null));
        }
    }

    @Test
    public void testHighlightedContentsRemainDirectXsltInput() throws Exception {
        try (Fixture f = new Fixture(true)) {
            MatchInfoDefs defs = new MatchInfoDefs();
            defs.register("capture", MatchInfo.Type.SPAN, f.field, null);
            HitsMutable hits = HitsMutable.create(new Hits.HitsContext(f.field, defs), -1, false, false);
            hits.add(0, 0, 1, new MatchInfo[] { SpanInfo.create(0, 1, f.field) });
            hits.add(0, 0, 1, new MatchInfo[] { SpanInfo.create(1, 2, f.field) });
            hits.add(0, 1, 1, new MatchInfo[] { SpanInfo.create(1, 1, f.field) });
            ResultDocContents result = f.result(0, -1, -1, f.query(hits));
            String expected = DataStream.XML_PROLOG + XML.replace("<w>B</w>", "<hl index=\"0\" start=\"0\" end=\"1\"><w>B</w></hl>");
            JsonNode json = Json.getJsonObjectMapper().readTree(render(result, DataFormat.JSON));
            assertEquals(1, json.size());
            assertEquals(expected, json.get("contents").asText());
            for (ApiVersion api: List.of(ApiVersion.V4_0, ApiVersion.V5_0)) {
                DataStream stream = DataStreamAbstract.create(DataFormat.XML, false, api);
                ResponseStreamer.get(stream, api).docContentsResponsePlain(result);
                assertEquals(expected, stream.getOutput());
                org.w3c.dom.Document xml = parseXml(stream.getOutput());
                assertEquals("root", xml.getDocumentElement().getNodeName());
                assertEquals(1, xml.getElementsByTagName("hl").getLength());
                assertEquals("B", xml.getElementsByTagName("hl").item(0).getTextContent());
                assertEquals("AB", xml.getDocumentElement().getTextContent());
                assertEquals("urn:input", xml.getDocumentElement().lookupNamespaceURI("bl"));
            }
            // A corpus stylesheet receives complete word elements, even in a bounded response.
            String xslt = """
                    <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                      <xsl:output omit-xml-declaration="yes"/>
                      <xsl:template match="/"><article><xsl:apply-templates/></article></xsl:template>
                      <xsl:template match="w"><span class="word"><xsl:value-of select="."/></span></xsl:template>
                      <xsl:template match="hl"><span class="hl" data-start="{@start}" data-end="{@end}"><xsl:apply-templates/></span></xsl:template>
                    </xsl:stylesheet>
                    """;
            String contents = render(f.result(0, 0, 1, f.query(hits)), DataFormat.XML);
            StringWriter html = new StringWriter();
            TransformerFactory.newInstance().newTransformer(new StreamSource(new StringReader(xslt)))
                    .transform(new StreamSource(new StringReader(contents)), new StreamResult(html));
            org.w3c.dom.Document transformed = parseXml(html.toString());
            assertEquals("AB", transformed.getDocumentElement().getTextContent());
            org.w3c.dom.Element highlight = (org.w3c.dom.Element) transformed.getElementsByTagName("span").item(1);
            assertEquals("hl", highlight.getAttribute("class"));
            assertEquals("0", highlight.getAttribute("data-start"));
            assertEquals("1", highlight.getAttribute("data-end"));
            assertEquals("B", highlight.getTextContent());
        }
    }

    @Test
    public void testPlaintextLegacyAndDocumentFallback() throws Exception {
        try (Fixture f = new Fixture(false)) {
            assertEquals(TEXT, render(f.result(1, -1, -1, null), DataFormat.XML));
            assertEquals("<raw>", render(f.result(1, 0, 1, null), DataFormat.XML));
            JsonNode contents = Json.getJsonObjectMapper().readTree(
                    render(f.result(0, 0, 1, null), DataFormat.JSON));
            assertEquals(1, contents.size());
            assertEquals("AB", parseXml(contents.get("contents").asText()).getDocumentElement().getTextContent());
            assertEquals(XML, f.result(0, 0, 1, null).getContent());
            when(f.metadata.usesSourceRangeVectors()).thenReturn(false);
            assertEquals(DataStream.XML_PROLOG + XML,
                    render(f.result(0, -1, -1, null), DataFormat.XML));
        }
    }

    private static org.w3c.dom.Document parseXml(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
    }

    @Test
    public void testAuthorizationAndAvailability() throws Exception {
        try (Fixture f = new Fixture(true)) {
            when(f.index.mayView(any())).thenReturn(false);
            assertThrows(NotAuthorized.class, () -> f.result(0, -1, -1, null));
            when(f.index.mayView(any())).thenReturn(true);
            when(f.field.hasContentStore()).thenReturn(false);
            assertEquals("CONTENT_NOT_AVAILABLE", assertThrows(BadRequest.class,
                    () -> f.result(0, -1, -1, null)).getBlsErrorCode());
        }
    }

    private static String render(ResultDocContents result, DataFormat format) {
        DataStream stream = DataStreamAbstract.create(format, false, ApiVersion.CURRENT);
        stream.startDocument(null);
        ResponseStreamer.get(stream, ApiVersion.CURRENT).docContentsResponsePlain(result);
        stream.endDocument();
        return stream.getOutput();
    }

    @Test
    public void testOriginalConcordanceGuardUsesDocumentSyntax() throws Exception {
        try (Fixture f = new Fixture(false)) {
            Hits xmlHits = Hits.single(new Hits.HitsContext(f.field), 0, 0, 1);
            ContextSize context = ContextSize.get(0, 10);
            assertThrows(UnsupportedConcordanceRepresentation.class,
                    () -> xmlHits.concordances(context, ConcordanceType.CONTENT_STORE));
            assertEquals("UNSUPPORTED_CONCORDANCE_REPRESENTATION", assertThrows(BadRequest.class,
                    () -> ConcordanceContext.get(xmlHits, ConcordanceType.CONTENT_STORE, context)).getBlsErrorCode());
            Hits textHits = Hits.single(new Hits.HitsContext(f.field), 1, 1, 2);
            assertEquals("text", ConcordanceContext.contentStoreConcordances(textHits, context)
                    .get(textHits.get(0)).match());
        }
    }

    @Test
    public void testPlaintextConcordanceEdgesAndLiteralMarkup() throws Exception {
        try (Fixture f = new Fixture(false)) {
            Hits hit = Hits.single(new Hits.HitsContext(f.field), 1, 0, 1);
            Concordance conc = ConcordanceContext.contentStoreConcordances(hit, ContextSize.get(10, 100))
                    .get(hit.get(0));
            assertEquals("", conc.left());
            assertEquals("<raw>", conc.match());
            assertEquals("& text", conc.right());
            assertFalse(conc.isXml());
            assertArrayEquals(new String[] { "", "<raw>", "& text" }, conc.partsNoXml());

            ResultDocSnippet snippet = new ResultDocSnippet(new RequestDocSnippet("1", f.field,
                    ContextSize.get(10, 100), true, 0, 1, 100, false, ConcordanceType.CONTENT_STORE, List.of()));
            for (ApiVersion api: List.of(ApiVersion.V4_0, ApiVersion.V5_0)) {
                DataStream stream = DataStreamAbstract.create(DataFormat.XML, false, api);
                stream.startDocument("response");
                ResponseStreamer response = ResponseStreamer.get(stream, api);
                response.snippet(snippet);
                stream.endDocument();
                org.w3c.dom.Document xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                        new InputSource(new StringReader(stream.getOutput())));
                assertEquals("<raw>", xml.getElementsByTagName("match").item(0).getTextContent());
                assertEquals("& text", xml.getElementsByTagName(response.KEY_AFTER).item(0).getTextContent());
            }

            hit = Hits.single(new Hits.HitsContext(f.field), 1, 0, 2);
            conc = ConcordanceContext.contentStoreConcordances(hit, ContextSize.get(0, 1)).get(hit.get(0));
            assertEquals("<raw>", conc.match());
            assertEquals("", conc.right());
            for (int position = 0; position <= 2; position++) {
                hit = Hits.single(new Hits.HitsContext(f.field), 1, position, position);
                conc = ConcordanceContext.contentStoreConcordances(hit, ContextSize.get(10, 100)).get(hit.get(0));
                assertEquals("", conc.match());
                assertEquals(TEXT, conc.left() + conc.right());
                conc = ConcordanceContext.contentStoreConcordances(hit, ContextSize.get(0, 100)).get(hit.get(0));
                assertEquals("", conc.left() + conc.match() + conc.right());
            }
        }
    }

    @Test
    public void testStructuralOriginalSnippetUsesConcordanceGuard() throws Exception {
        try (Fixture f = new Fixture(true)) {
            ResultDocSnippet snippet = f.snippet(0, 0, 1, ContextSize.get(10, 100), 100);
            assertEquals("UNSUPPORTED_CONCORDANCE_REPRESENTATION", assertThrows(BadRequest.class,
                    () -> render(snippet, DataFormat.JSON)).getBlsErrorCode());
            // A normal FI snippet still resolves the requested token context and uses its existing schema.
            ResultDocSnippet fi = new ResultDocSnippet(new RequestDocSnippet("0", f.field,
                    ContextSize.get(1, 100), true, 0, 1, 100, false, ConcordanceType.FORWARD_INDEX, List.of()));
            assertFalse(fi.isOrigContent());
            assertEquals(0, fi.getHits().get(0).start());
            assertEquals(1, fi.getHits().get(0).end());
            assertEquals(1, fi.getContext().after());
        }
    }

    private static String render(ResultDocSnippet result, DataFormat format) {
        DataStream stream = DataStreamAbstract.create(format, false, ApiVersion.CURRENT);
        stream.startDocument(null);
        ResponseStreamer.get(stream, ApiVersion.CURRENT).snippet(result);
        stream.endDocument();
        return stream.getOutput();
    }

    private static class Fixture implements AutoCloseable {
        final Directory directory = new ByteBuffersDirectory();
        final DirectoryReader reader;
        final BlackLabIndex index = mock(BlackLabIndex.class);
        final IndexMetadata metadata = mock(IndexMetadata.class);
        final AnnotatedField field = mock(AnnotatedField.class);

        Fixture(boolean containers) throws Exception {
            String[] sources = { XML, TEXT, "<empty/>" };
            int[][] starts = { { XML.indexOf("<w>B"), XML.indexOf("<w>A") }, { 0, 7 }, {} };
            int[][] ends = { { starts[0][0] + 8, starts[0][1] + 8 }, { 5, TEXT.length() }, {} };
            DocWriter docWriter = mock(DocWriter.class);
            when(docWriter.indexObjectFactory()).thenReturn(BLIndexObjectFactoryLucene.INSTANCE);
            try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new KeywordAnalyzer()))) {
                for (int d = 0; d < sources.length; d++) {
                    AnnotatedFieldWriter fieldWriter = new AnnotatedFieldWriter(docWriter, FIELD, "word",
                            AnnotationSensitivities.ONLY_INSENSITIVE,
                            AnnotatedFieldWriter.TokenOffsetStorage.SOURCE_RANGE_VECTORS, false, false, null);
                    for (int i = 0; i < starts[d].length; i++) {
                        fieldWriter.addStartChar(starts[d][i]);
                        fieldWriter.mainAnnotation().addValue("word" + i);
                        fieldWriter.addEndChar(ends[d][i]);
                    }
                    fieldWriter.mainAnnotation().addValue("");
                    fieldWriter.addFinalStartEndChars();
                    BLInputDocumentLucene doc = new BLInputDocumentLucene(BLInputDocument.DocType.DOCUMENT);
                    fieldWriter.addToDoc(doc);
                    doc.addStoredNumericField(AnnotatedFieldNameUtil.sourceStatusField(FIELD),
                            SourceRangeEncoding.DOC_FLAG_SOURCE_RANGE_VECTORS |
                                    (d == 1 ? 0 : SourceRangeEncoding.DOC_FLAG_XML), false);
                    if (containers && d != 1)
                        doc.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), d == 2 ? new byte[0] :
                                SourceUnitCodec.encode(IntLists.mutable.of(0, XML.length(), 2)));
                    writer.addDocument(doc.getDocument());
                }
            }
            reader = DirectoryReader.open(directory);
            when(index.reader()).thenReturn(reader);
            when(index.metadata()).thenReturn(metadata);
            when(metadata.usesSourceRangeVectors()).thenReturn(true);
            when(index.mainAnnotatedField()).thenReturn(field);
            when(field.name()).thenReturn(FIELD);
            when(field.index()).thenReturn(index);
            when(field.tokenLengthField()).thenReturn(AnnotatedFieldNameUtil.lengthTokensField(FIELD));
            when(field.hasContentStore()).thenReturn(true);
            when(index.getDocIdFromPid(anyString())).thenAnswer(i -> Integer.parseInt(i.getArgument(0)));
            when(index.docExists(anyInt())).thenReturn(true);
            when(index.luceneDoc(anyInt())).thenAnswer(i -> reader.storedFields().document(i.getArgument(0)));
            when(index.mayView(any())).thenReturn(true);
            when(index.defaultUnbalancedTagsStrategy()).thenReturn(XmlHighlighter.UnbalancedTagsStrategy.ADD_TAG);
            ContentStore store = (doc, start, end) -> new String[] { sources[doc].substring(
                    Math.max(0, start[0]), end[0] < 0 ? sources[doc].length() : Math.min(sources[doc].length(), end[0])) };
            when(index.contentStore(field)).thenReturn(store);
        }

        ResultDocContents result(int doc, int start, int end, RequestHits query) throws Exception {
            return new ResultDocContents(new RequestDocContents(index, field, query, Integer.toString(doc),
                    start, end, null));
        }

        ResultDocSnippet snippet(int doc, int start, int end, ContextSize context, int max) {
            return new ResultDocSnippet(new RequestDocSnippet(Integer.toString(doc), field, context, true,
                    start, end, max, false, ConcordanceType.CONTENT_STORE, List.of()));
        }

        RequestHits query(Hits hits) throws Exception {
            RequestHits request = mock(RequestHits.class);
            SearchHits search = mock(SearchHits.class);
            HitResults results = mock(HitResults.class);
            when(request.getSearch()).thenReturn(search);
            when(search.execute()).thenReturn(results);
            when(results.getHits()).thenReturn(hits);
            return request;
        }

        @Override
        public void close() throws Exception {
            reader.close();
            directory.close();
        }
    }
}
