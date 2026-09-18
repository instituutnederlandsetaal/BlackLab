package nl.inl.blacklab.search;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.BLInputDocumentLucene;
import nl.inl.blacklab.index.BLIndexObjectFactory;
import nl.inl.blacklab.index.BLIndexObjectFactoryLucene;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.SourceRangeEncoding;
import nl.inl.util.UtilsForTesting;

public class TestSourceRangeReader {

    @Test
    public void testUnknownCodecRejectedBeforeAppendWriter() throws Exception {
        try (var directory = UtilsForTesting.createBlackLabTestDir("unknown-source-codec")) {
            try (BlackLabIndexWriter writer = BlackLab.openForWriting(directory.file(), true)) {
                writer.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, "source-range-vectors-future");
                writer.metadata().save();
            }
            try (Directory lucene = FSDirectory.open(directory.file().toPath())) {
                long generation = DirectoryReader.listCommits(lucene).get(0).getGeneration();
                Assert.assertThrows(InvalidIndex.class, () -> BlackLab.open(directory.file()));
                Assert.assertThrows(InvalidIndex.class, () -> BlackLab.openForWriting(directory.file(), false));
                Assert.assertEquals(generation, DirectoryReader.listCommits(lucene).get(0).getGeneration());
                // A rejected append must not leave a write lock behind.
                try (IndexWriter writer = new IndexWriter(lucene, new IndexWriterConfig(new KeywordAnalyzer()))) {
                    writer.rollback();
                }
            }
        }
    }

    @Test
    public void testUnsupportedBackendCannotCreateSourceRangeIndex() {
        try (var directory = UtilsForTesting.createBlackLabTestDir("unsupported-source-backend");
             var engine = new BlackLabEngine(1)) {
            var factory = Mockito.mock(BLIndexObjectFactory.class);
            engine.setIndexObjectFactory(factory);
            Assert.assertThrows(InvalidIndex.class, () -> engine.openForWriting(directory.file(), true));
            Assert.assertFalse(BlackLabIndex.isIndex(directory.file()));
            Mockito.verify(factory, Mockito.never()).indexWriterProxy(Mockito.any(), Mockito.any());
        }
    }

    @Test
    public void testRequestedRangesAndValidation() throws Exception {
        String annotatedFieldName = "contents";
        DocWriter docWriter = Mockito.mock(DocWriter.class);
        Mockito.when(docWriter.indexObjectFactory()).thenReturn(BLIndexObjectFactoryLucene.INSTANCE);
        AnnotatedFieldWriter field = new AnnotatedFieldWriter(docWriter, annotatedFieldName, "word",
                AnnotationSensitivities.ONLY_INSENSITIVE,
                AnnotatedFieldWriter.TokenOffsetStorage.SOURCE_RANGE_VECTORS, false, false, null);
        int[] starts = { 11, 2, 30 };
        int[] ends = { 15, 8, 37 };
        for (int i = 0; i < starts.length; i++) {
            field.addStartChar(starts[i]);
            field.mainAnnotation().addValue("word" + i);
            field.addEndChar(ends[i]);
        }
        field.mainAnnotation().addValue("");
        field.addFinalStartEndChars();
        BLInputDocumentLucene document = new BLInputDocumentLucene(BLInputDocument.DocType.DOCUMENT);
        field.addToDoc(document);
        document.addStoredNumericField(AnnotatedFieldNameUtil.sourceStatusField("contents"),
                SourceRangeEncoding.DOC_FLAG_XML | SourceRangeEncoding.DOC_FLAG_SOURCE_RANGE_VECTORS, false);

        try (Directory directory = new ByteBuffersDirectory()) {
            try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new KeywordAnalyzer()))) {
                writer.addDocument(document.getDocument());
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                String rangeField = AnnotatedFieldNameUtil.sourceRangesField(annotatedFieldName);
                int[] requestedStarts = { 2, 0, 2 };
                int[] requestedEnds = { 1, 0 };
                SourceRangeReader.characterOffsets(reader, 0, rangeField, starts.length,
                        requestedStarts, requestedEnds, false);
                Assert.assertArrayEquals(new int[] { 30, 11, 30 }, requestedStarts);
                Assert.assertArrayEquals(new int[] { 8, 15 }, requestedEnds);

                assertInvalid(() -> SourceRangeReader.characterOffsets(reader, 0, rangeField,
                        starts.length + 1, new int[] { 0 }, new int[0], false));
                assertInvalid(() -> SourceRangeReader.characterOffsets(reader, 0, rangeField,
                        starts.length, new int[] { starts.length }, new int[0], false));

                BlackLabIndex index = Mockito.mock(BlackLabIndex.class);
                IndexMetadata metadata = Mockito.mock(IndexMetadata.class);
                AnnotatedField mainField = Mockito.mock(AnnotatedField.class);
                AnnotatedField requestedField = Mockito.mock(AnnotatedField.class);
                Mockito.when(index.reader()).thenReturn(reader);
                Mockito.when(index.metadata()).thenReturn(metadata);
                Mockito.when(metadata.usesSourceRangeVectors()).thenReturn(true);
                Mockito.when(index.mainAnnotatedField()).thenReturn(mainField);
                Mockito.when(mainField.name()).thenReturn("contents");
                Mockito.when(requestedField.name()).thenReturn(annotatedFieldName);
                Mockito.when(requestedField.tokenLengthField()).thenReturn(
                        AnnotatedFieldNameUtil.lengthTokensField(annotatedFieldName));
                int[] dispatchedStarts = { 2 };
                int[] dispatchedEnds = { 0 };
                DocUtil.characterOffsets(index, 0, requestedField, dispatchedStarts, dispatchedEnds, false);
                Assert.assertArrayEquals(new int[] { 30 }, dispatchedStarts);
                Assert.assertArrayEquals(new int[] { 15 }, dispatchedEnds);

                int[] boundaryStarts = { -1, 0, 3, Integer.MAX_VALUE };
                int[] boundaryEnds = { -1, 2, 3, Integer.MAX_VALUE };
                DocUtil.characterOffsets(index, 0, requestedField, boundaryStarts, boundaryEnds, true);
                Assert.assertArrayEquals(new int[] { 11, 11, 37, 37 }, boundaryStarts);
                Assert.assertArrayEquals(new int[] { 11, 37, 37, 37 }, boundaryEnds);

                // A zero-token field has no range vector; boundary defaults need no vector access.
                SourceRangeReader.characterOffsets(reader, 0, "absent", 0, boundaryStarts, boundaryEnds, true);
                Assert.assertArrayEquals(new int[4], boundaryStarts);
                Assert.assertArrayEquals(new int[4], boundaryEnds);
            }
        }
    }

    private static void assertInvalid(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected corrupt source ranges to be rejected");
        } catch (InvalidIndex e) {
            // expected
        }
    }
}
