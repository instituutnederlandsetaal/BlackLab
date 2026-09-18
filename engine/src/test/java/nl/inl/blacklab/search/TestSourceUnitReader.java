package nl.inl.blacklab.search;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.junit.Test;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.BLInputDocumentLucene;
import nl.inl.blacklab.index.BLIndexObjectFactoryLucene;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.search.SourceRangeReader.SourceWindow;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.SourceRangeEncoding;
import nl.inl.blacklab.search.indexmetadata.SourceUnitCodec;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.util.XmlHighlighter;

public class TestSourceUnitReader {

    private static final String MAIN_FIELD = "contents";

    private static final String FIELD = "contents__nl";

    @Test
    public void testFragmentChildrenDoNotChangeParentSourceRanges() throws Exception {
        String xml = "<root><a><w>A</w></a><b><w>B</w></b></root>";
        int a = xml.indexOf("<w>A");
        int b = xml.indexOf("<w>B");
        BLInputDocumentLucene parent = documentWithRanges(
                new int[] { b, a }, new int[] { b + 8, a + 8 }, true);
        parent.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), SourceUnitCodec.encode(
                IntLists.mutable.of(xml.indexOf("<b>"), xml.indexOf("</b>") + 4, 1,
                        xml.indexOf("<a>"), xml.indexOf("</a>") + 4, 2)));
        BLInputDocumentLucene fragment = new BLInputDocumentLucene(BLInputDocument.DocType.FRAGMENT);
        fragment.addIndexedAndDocValues(BLInputDocument.FRAG_FIELD_ANNOTATED_FIELD, FIELD);
        fragment.addNumericField(BLInputDocument.FRAG_FIELD_START, 0, false, false, true);
        fragment.addNumericField(BLInputDocument.FRAG_FIELD_END, 1, false, false, true);

        try (Directory directory = new ByteBuffersDirectory()) {
            IndexWriterConfig config = new IndexWriterConfig(new KeywordAnalyzer());
            config.setMergePolicy(NoMergePolicy.INSTANCE);
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                writer.addDocument(new BLInputDocumentLucene(BLInputDocument.DocType.DOCUMENT).getDocument());
                writer.commit();
                writer.addDocuments(List.of(fragment.getDocument(), parent.getDocument()));
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                assertEquals(2, reader.leaves().size());
                int parentId = 2;
                TestIndex context = testIndex(reader);
                useContent(context, "", "", xml);
                int[] starts = { 0, 1 };
                int[] ends = { 0, 1 };
                DocUtil.characterOffsets(context.index(), parentId, context.field(), starts, ends, false);
                assertArrayEquals(new int[] { b, a }, starts);
                assertArrayEquals(new int[] { b + 8, a + 8 }, ends);
                Hits hits = Hits.single(new Hits.HitsContext(context.field()), parentId, 0, 1);
                assertEquals("<b><hl index=\"0\" start=\"0\" end=\"1\"><w>B</w></hl></b>",
                        DocUtil.xmlContents(context.index(), parentId, context.field(), 0, 1, hits));
            }
        }
    }

    @Test
    public void testContainerSelectionReturnsSeparateRangesInSourceOrder() throws Exception {
        BLInputDocumentLucene document = documentWithBookkeeping(5);
        document.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), SourceUnitCodec.encode(
                IntLists.mutable.of(200, 220, 2, 20, 30, 3, 100, 120, 5)));

        try (Directory directory = index(document); DirectoryReader reader = DirectoryReader.open(directory)) {
            TestIndex context = testIndex(reader);
            assertEquals(List.of(new SourceWindow(20, 30), new SourceWindow(100, 120), new SourceWindow(200, 220)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 1, 4));
            assertEquals(List.of(new SourceWindow(20, 30)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 2, 3));
            assertEquals(List.of(new SourceWindow(200, 220)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 0, 1));
            assertEquals(List.of(new SourceWindow(-1, -1)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), -1, -1));
            assertEquals(List.of(new SourceWindow(20, 30), new SourceWindow(200, 220)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), -1, 3));
            assertEquals(List.of(new SourceWindow(20, 30), new SourceWindow(100, 120)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 2, -1));
            // Zero-width hits still have a renderable container at either document boundary.
            assertEquals(List.of(new SourceWindow(200, 220)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 0, 0));
            assertEquals(List.of(new SourceWindow(20, 30)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 2, 2));
            assertEquals(List.of(new SourceWindow(100, 120)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 5, 5));
        }
    }

    @Test
    public void testNestedContainersAreReturnedOnce() throws Exception {
        BLInputDocumentLucene document = documentWithBookkeeping(4);
        document.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), SourceUnitCodec.encode(
                IntLists.mutable.of(20, 30, 1, 0, 100, 2, 0, 100, 3, 120, 130, 4)));
        try (Directory directory = index(document); DirectoryReader reader = DirectoryReader.open(directory)) {
            TestIndex context = testIndex(reader);
            assertEquals(List.of(new SourceWindow(0, 100), new SourceWindow(120, 130)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 0, 4));
        }
    }

    @Test
    public void testWholeDocumentDoesNotLoadContainerData() throws Exception {
        var reader = mock(org.apache.lucene.index.IndexReader.class);
        var storedFields = mock(org.apache.lucene.index.StoredFields.class);
        var document = new org.apache.lucene.document.Document();
        for (var field: documentWithBookkeeping(5).getDocument()) {
            if (field.fieldType().stored())
                document.add(field);
        }
        when(reader.storedFields()).thenReturn(storedFields);
        when(storedFields.document(0, java.util.Set.of(
                AnnotatedFieldNameUtil.sourceStatusField(MAIN_FIELD),
                AnnotatedFieldNameUtil.lengthTokensField(FIELD)))).thenReturn(document);
        TestIndex context = testIndex(reader);
        assertEquals(List.of(new SourceWindow(-1, -1)),
                SourceUnitReader.sourceRanges(context.index(), 0, context.field(), -1, -1));
    }

    @Test
    public void testAbsentContainerReturnsWholeDocument() throws Exception {
        String xml = "<root><a><w>A</w></a><b><w>B</w></b></root>";
        int a = xml.indexOf("<w>A");
        int b = xml.indexOf("<w>B");
        BLInputDocumentLucene document = documentWithRanges(
                new int[] { b, a }, new int[] { b + 8, a + 8 }, true);
        try (Directory directory = index(document); DirectoryReader reader = DirectoryReader.open(directory)) {
            TestIndex context = testIndex(reader);
            useContent(context, xml);
            assertEquals(List.of(new SourceWindow(-1, -1)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 0, 1));
            assertEquals(xml, DocUtil.xmlContents(context.index(), 0, context.field(), 0, 1, null));
            // Both hit tokens are visible because the returned document contains both.
            Hits hit = Hits.single(new Hits.HitsContext(context.field()), 0, 0, 2);
            assertEquals("<root><a><hl index=\"0\" start=\"1\" end=\"2\"><w>A</w></hl></a>" +
                            "<b><hl index=\"1\" start=\"0\" end=\"1\"><w>B</w></hl></b></root>",
                    DocUtil.xmlContents(context.index(), 0, context.field(), 0, 1, hit));
        }
    }

    @Test
    public void testEmptyDocumentsAndInvalidRequests() throws Exception {
        BLInputDocumentLucene explicit = documentWithBookkeeping(0);
        explicit.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), new byte[0]);
        BLInputDocumentLucene defaultContainer = documentWithBookkeeping(0);
        try (Directory directory = index(explicit, defaultContainer); DirectoryReader reader = DirectoryReader.open(directory)) {
            TestIndex context = testIndex(reader);
            for (int docId = 0; docId < 2; docId++) {
                assertEquals(List.of(new SourceWindow(-1, -1)),
                        SourceUnitReader.sourceRanges(context.index(), docId, context.field(), 0, 0));
            }
            assertThrows(IllegalArgumentException.class,
                    () -> SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 0, 1));
            assertEquals(List.of(new SourceWindow(-1, -1)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), -1, 0));
            assertEquals(List.of(new SourceWindow(-1, -1)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 0, -1));
        }
    }

    @Test
    public void testRejectsInvalidRanges() throws Exception {
        BLInputDocumentLucene document = documentWithBookkeeping(2);
        try (Directory directory = index(document); DirectoryReader reader = DirectoryReader.open(directory)) {
            TestIndex context = testIndex(reader);
            assertThrows(IllegalArgumentException.class,
                    () -> SourceUnitReader.sourceRanges(context.index(), 0, context.field(), -2, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 0, -2));
            assertThrows(IllegalArgumentException.class,
                    () -> SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 2, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 0, 3));
        }
    }

    @Test
    public void testNonXmlDocumentDoesNotUseStructuralRetrieval() throws Exception {
        BLInputDocumentLucene document = documentWithBookkeeping(1, false);
        try (Directory directory = index(document); DirectoryReader reader = DirectoryReader.open(directory)) {
            TestIndex context = testIndex(reader);
            assertFalse(DocUtil.hasStructuralSource(context.index(), 0, context.field()));
        }
    }

    @Test
    public void testIndependentContainersExcludeIncidentalSourceAndAncestors() throws Exception {
        String first = "<s id='first'><w>A</w></s>";
        String last = "<s id='last'><w>B</w></s>";
        // Deliberately malformed out-of-scope material proves retrieval never parses or repairs the gap.
        String xml = "<root><a source='keep-out'>" + first + "</a><broken not xml >>>" +
                "<b source='also-out'>" + last + "</b></root>";
        int a = xml.indexOf("<w>A");
        int b = xml.indexOf("<w>B");
        BLInputDocumentLucene document = documentWithRanges(
                new int[] { b, a }, new int[] { b + 8, a + 8 }, true);
        document.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), SourceUnitCodec.encode(
                IntLists.mutable.of(xml.indexOf(last), xml.indexOf(last) + last.length(), 1,
                        xml.indexOf(first), xml.indexOf(first) + first.length(), 2)));
        try (Directory directory = index(document); DirectoryReader reader = DirectoryReader.open(directory)) {
            TestIndex context = testIndex(reader);
            useContent(context, xml);
            assertEquals(first + last, DocUtil.xmlContents(context.index(), 0, context.field(), 0, 2, null));
            assertEquals(last, DocUtil.xmlContents(context.index(), 0, context.field(), 0, 1, null));
            Hits hit = Hits.single(new Hits.HitsContext(context.field()), 0, 0, 2);
            assertEquals(first.replace("<w>A</w>", "<hl index=\"0\" start=\"1\" end=\"2\"><w>A</w></hl>") +
                            last.replace("<w>B</w>", "<hl index=\"0\" start=\"0\" end=\"1\"><w>B</w></hl>"),
                    DocUtil.xmlContents(context.index(), 0, context.field(), 0, 2, hit));
            assertEquals(xml, DocUtil.xmlContents(context.index(), 0, context.field(), -1, -1, null));
        }
    }

    @Test
    public void testHighlightsFollowReturnedSourceAcrossNestedContainerTokenOrder() throws Exception {
        String inner = "<inner><w>B</w></inner>";
        String outer = "<outer><w>A</w>" + inner + "</outer>";
        String other = "<other><w>C</w></other>";
        String xml = "<doc>" + outer + other + "</doc>";
        // The nested container is indexed last, after a container outside the requested XML.
        int a = xml.indexOf("<w>A"), b = xml.indexOf("<w>B"), c = xml.indexOf("<w>C");
        BLInputDocumentLucene document = documentWithRanges(
                new int[] { a, c, b }, new int[] { a + 8, c + 8, b + 8 }, true);
        document.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), SourceUnitCodec.encode(
                IntLists.mutable.of(xml.indexOf(outer), xml.indexOf(outer) + outer.length(), 1,
                        xml.indexOf(other), xml.indexOf(other) + other.length(), 2,
                        xml.indexOf(inner), xml.indexOf(inner) + inner.length(), 3)));
        try (Directory directory = index(document); DirectoryReader reader = DirectoryReader.open(directory)) {
            TestIndex context = testIndex(reader);
            useContent(context, xml);
            Hits hits = Hits.single(new Hits.HitsContext(context.field()), 0, 0, 3);
            assertEquals(outer.replace("<w>A</w>", "<hl index=\"0\" start=\"0\" end=\"1\"><w>A</w></hl>")
                            .replace("<w>B</w>", "<hl index=\"1\" start=\"2\" end=\"3\"><w>B</w></hl>"),
                    DocUtil.xmlContents(context.index(), 0, context.field(), 0, 1, hits));
        }
    }

    @Test
    public void testOverlappingHitsMarkEachReorderedTokenOnlyOnce() throws Exception {
        String xml = "<root xmlns:bl=\"urn:other\"><a><w bl:x=\"x>y\">A</w></a><b><w>B</w></b></root>";
        int aStart = xml.indexOf("<w bl:x");
        int aEnd = xml.indexOf("</w>", aStart) + "</w>".length();
        int bStart = xml.indexOf("<w>", aEnd);
        int bEnd = xml.indexOf("</w>", bStart) + "</w>".length();
        BLInputDocumentLucene document = documentWithRanges(
                new int[] { bStart, aStart }, new int[] { bEnd, aEnd }, true);
        document.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), SourceUnitCodec.encode(
                IntLists.mutable.of(0, xml.length(), 2)));
        try (Directory directory = index(document); DirectoryReader reader = DirectoryReader.open(directory)) {
            TestIndex context = testIndex(reader);
            useContent(context, xml);
            int[] hitDocs = new int[100];
            int[] hitStarts = new int[100];
            int[] hitEnds = new int[100];
            java.util.Arrays.fill(hitEnds, 2);
            Hits hits = Hits.fromLists(context.field(), hitDocs, hitStarts, hitEnds);
            assertEquals("<root xmlns:bl=\"urn:other\"><a><hl index=\"0\" start=\"1\" end=\"2\"><w bl:x=\"x>y\">A</w></hl></a>" +
                            "<b><hl index=\"1\" start=\"0\" end=\"1\"><w>B</w></hl></b></root>",
                    DocUtil.xmlContents(context.index(), 0, context.field(), 0, 1, hits));
            Hits zeroWidth = Hits.fromLists(context.field(), new int[] { 0, 0, 0 },
                    new int[] { 0, 1, 2 }, new int[] { 0, 1, 2 });
            assertEquals(xml, DocUtil.xmlContents(context.index(), 0, context.field(), 0, 1, zeroWidth));
        }
    }

    @Test
    public void testDenseOverlapOutputDependsOnTokensNotHitCount() throws Exception {
        int tokenCount = 100;
        StringBuilder xml = new StringBuilder("<doc>");
        int[] starts = new int[tokenCount];
        int[] ends = new int[tokenCount];
        for (int token = tokenCount - 1; token >= 0; token--) {
            starts[token] = xml.length();
            xml.append("<w n='").append(token).append("'>word").append(token).append("</w>");
            ends[token] = xml.length();
        }
        xml.append("</doc>");
        BLInputDocumentLucene document = documentWithRanges(starts, ends, true);
        document.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), SourceUnitCodec.encode(
                IntLists.mutable.of(0, xml.length(), tokenCount)));
        try (Directory directory = index(document); DirectoryReader reader = DirectoryReader.open(directory)) {
            TestIndex context = testIndex(reader);
            useContent(context, xml.toString());
            int[] hitDocs = new int[1_000];
            int[] hitStarts = new int[1_000];
            int[] hitEnds = new int[1_000];
            java.util.Arrays.fill(hitEnds, tokenCount);
            Hits repeated = Hits.fromLists(context.field(), hitDocs, hitStarts, hitEnds);
            String content = DocUtil.xmlContents(context.index(), 0, context.field(), 0, 1, repeated);
            assertEquals(tokenCount, content.split("<hl ", -1).length - 1);
            assertEquals(xml.toString(), content.replaceAll("<hl[^>]*>", "").replace("</hl>", ""));
            Hits single = Hits.single(new Hits.HitsContext(context.field()), 0, 0, tokenCount);
            assertEquals(DocUtil.xmlContents(context.index(), 0, context.field(), 0, 1, single), content);
            assertEquals(5_963, content.length());
        }
    }

    @Test
    public void testLiteralTextHighlightingAndEmptyHits() throws Exception {
        String text = "  <root>one & two</root>  ";
        int one = text.indexOf("one");
        int two = text.indexOf("two");
        BLInputDocumentLucene document = documentWithRanges(
                new int[] { one, one, two }, new int[] { one, one + 3, two + 3 }, false);
        try (Directory directory = index(document); DirectoryReader reader = DirectoryReader.open(directory)) {
            TestIndex context = testIndex(reader);
            useContent(context, text);
            Hits hits = Hits.fromLists(context.field(), new int[] { 0, 0, 0, 0 },
                    new int[] { 0, 0, 1, 3 }, new int[] { 0, 1, 3, 3 });
            assertEquals("  <root><hl index=\"0\" start=\"1\" end=\"3\">one & two</hl></root>  ",
                    DocUtil.highlightTextContent(context.index(), 0, hits, -1, -1));
            assertEquals("two</root>", new XmlHighlighter().highlightPlainText("two</root>", List.of(), 0));
            assertEquals("<hl index=\"0\">a<open>b</hl>", new XmlHighlighter().highlightPlainText("a<open>b",
                    List.of(new XmlHighlighter.HitCharSpan(0, 8)), 0));
            Hits emptyHits = Hits.fromLists(context.field(), new int[] { 0, 0 },
                    new int[] { 0, 3 }, new int[] { 0, 3 });
            assertEquals(text, DocUtil.highlightTextContent(context.index(), 0, emptyHits, -1, -1));
            assertEquals(text, DocUtil.highlightTextContent(context.index(), 0,
                    Hits.single(new Hits.HitsContext(context.field()), 0, 0, 1), -1, -1));
            assertEquals("  <root>one", DocUtil.highlightTextContent(context.index(), 0, emptyHits, -1, 2));
            assertEquals("one & two</root>  ", DocUtil.highlightTextContent(context.index(), 0, emptyHits, 1, -1));
        }
    }

    @Test
    public void testRejectsMalformedSelectedUnits() throws Exception {
        BLInputDocumentLucene truncated = documentWithBookkeeping(1);
        truncated.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), new byte[1]);
        BLInputDocumentLucene backwards = documentWithBookkeeping(3);
        backwards.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), SourceUnitCodec.encode(
                IntLists.mutable.of(0, 10, 2, 20, 30, 1)));
        BLInputDocumentLucene empty = documentWithBookkeeping(1);
        empty.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), new byte[0]);
        BLInputDocumentLucene crossing = documentWithBookkeeping(2);
        crossing.addStoredField(AnnotatedFieldNameUtil.sourceUnitsField(FIELD), SourceUnitCodec.encode(
                IntLists.mutable.of(0, 20, 1, 10, 30, 2)));
        try (Directory directory = index(truncated, backwards, empty, crossing);
                DirectoryReader reader = DirectoryReader.open(directory)) {
            TestIndex context = testIndex(reader);
            assertThrows(InvalidIndex.class,
                    () -> SourceUnitReader.sourceRanges(context.index(), 0, context.field(), 0, 1));
            assertEquals(List.of(new SourceWindow(-1, -1)),
                    SourceUnitReader.sourceRanges(context.index(), 0, context.field(), -1, -1));
            assertThrows(InvalidIndex.class,
                    () -> SourceUnitReader.sourceRanges(context.index(), 1, context.field(), 0, 3));
            assertThrows(InvalidIndex.class,
                    () -> SourceUnitReader.sourceRanges(context.index(), 2, context.field(), 0, 1));
            assertThrows(InvalidIndex.class,
                    () -> SourceUnitReader.sourceRanges(context.index(), 3, context.field(), 0, 2));
        }
    }

    private static void useContent(TestIndex context, String... sources) {
        ContentStore contentStore = (docId, starts, ends) -> {
            String[] parts = new String[starts.length];
            for (int i = 0; i < starts.length; i++) {
                parts[i] = sources[docId].substring(starts[i] < 0 ? 0 : starts[i],
                        ends[i] < 0 ? sources[docId].length() : ends[i]);
            }
            return parts;
        };
        when(context.field().hasContentStore()).thenReturn(true);
        when(context.index().contentStore(context.field())).thenReturn(contentStore);
    }

    private static BLInputDocumentLucene documentWithBookkeeping(int realTokenCount) {
        return documentWithBookkeeping(realTokenCount, true);
    }

    private static BLInputDocumentLucene documentWithBookkeeping(int realTokenCount, boolean xml) {
        BLInputDocumentLucene document = new BLInputDocumentLucene(BLInputDocument.DocType.DOCUMENT);
        addStatus(document, xml);
        document.addStoredNumericField(AnnotatedFieldNameUtil.lengthTokensField(FIELD), realTokenCount + 1, true);
        return document;
    }

    private static BLInputDocumentLucene documentWithRanges(int[] starts, int[] ends, boolean xml) {
        DocWriter docWriter = mock(DocWriter.class);
        when(docWriter.indexObjectFactory()).thenReturn(BLIndexObjectFactoryLucene.INSTANCE);
        AnnotatedFieldWriter fieldWriter = new AnnotatedFieldWriter(docWriter, FIELD, "word",
                AnnotationSensitivities.ONLY_INSENSITIVE,
                AnnotatedFieldWriter.TokenOffsetStorage.SOURCE_RANGE_VECTORS, false, false, null);
        for (int i = 0; i < starts.length; i++) {
            fieldWriter.addStartChar(starts[i]);
            fieldWriter.mainAnnotation().addValue("word" + i);
            fieldWriter.addEndChar(ends[i]);
        }
        fieldWriter.mainAnnotation().addValue("");
        fieldWriter.addFinalStartEndChars();
        BLInputDocumentLucene document = new BLInputDocumentLucene(BLInputDocument.DocType.DOCUMENT);
        fieldWriter.addToDoc(document);
        addStatus(document, xml);
        return document;
    }

    private static void addStatus(BLInputDocumentLucene document) {
        addStatus(document, true);
    }

    private static void addStatus(BLInputDocumentLucene document, boolean xml) {
        document.addStoredNumericField(AnnotatedFieldNameUtil.sourceStatusField(MAIN_FIELD),
                (xml ? SourceRangeEncoding.DOC_FLAG_XML : 0) |
                        SourceRangeEncoding.DOC_FLAG_SOURCE_RANGE_VECTORS, false);
    }

    private static Directory index(BLInputDocumentLucene... documents) throws Exception {
        Directory directory = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new KeywordAnalyzer()))) {
            for (BLInputDocumentLucene document: documents)
                writer.addDocument(document.getDocument());
        }
        return directory;
    }

    private static TestIndex testIndex(org.apache.lucene.index.IndexReader reader) {
        BlackLabIndex index = mock(BlackLabIndex.class);
        IndexMetadata metadata = mock(IndexMetadata.class);
        AnnotatedField mainField = mock(AnnotatedField.class);
        AnnotatedField field = mock(AnnotatedField.class);
        when(index.reader()).thenReturn(reader);
        when(index.metadata()).thenReturn(metadata);
        when(metadata.usesSourceRangeVectors()).thenReturn(true);
        when(index.mainAnnotatedField()).thenReturn(mainField);
        when(mainField.name()).thenReturn(MAIN_FIELD);
        when(field.name()).thenReturn(FIELD);
        when(field.tokenLengthField()).thenReturn(AnnotatedFieldNameUtil.lengthTokensField(FIELD));
        return new TestIndex(index, field);
    }

    private record TestIndex(BlackLabIndex index, AnnotatedField field) {}
}
