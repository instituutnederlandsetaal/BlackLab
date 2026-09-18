package nl.inl.blacklab.index.annotated;

import java.util.Arrays;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import nl.inl.blacklab.codec.blacklab50.BlackLab50Codec;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.BLInputDocumentLucene;
import nl.inl.blacklab.index.BLIndexObjectFactory;
import nl.inl.blacklab.index.BLIndexObjectFactoryLucene;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.SourceRangeEncoding;

public class TestTokenStreamSourceRanges {

    @Test
    public void testLuceneRoundTrip() throws Exception {
        String annotatedFieldName = "contents";
        String fieldName = AnnotatedFieldNameUtil.sourceRangesField(annotatedFieldName);
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

        try (Directory directory = new ByteBuffersDirectory()) {
            IndexWriterConfig config = new IndexWriterConfig(new KeywordAnalyzer());
            config.setCodec(new BlackLab50Codec());
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                writer.addDocument(document.getDocument());
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                FieldInfo fieldInfo = FieldInfos.getMergedFieldInfos(reader).fieldInfo(fieldName);
                Assert.assertEquals(IndexOptions.DOCS, fieldInfo.getIndexOptions());
                Assert.assertFalse(fieldInfo.hasPayloads());
                Assert.assertTrue(fieldInfo.hasVectors());

                Terms terms = reader.termVectors().get(0, fieldName);
                Assert.assertEquals(1, terms.size());
                Assert.assertTrue(terms.hasPositions());
                Assert.assertTrue(terms.hasPayloads());
                Assert.assertFalse(terms.hasOffsets());
                TermsEnum termsEnum = terms.iterator();
                Assert.assertTrue(termsEnum.seekExact(new BytesRef(SourceRangeEncoding.TERM)));
                PostingsEnum postings = termsEnum.postings(null, PostingsEnum.ALL);
                Assert.assertEquals(0, postings.nextDoc());
                Assert.assertEquals(3, postings.freq());
                byte[][] expected = {
                        { 0, 0, 0, 11, 0, 0, 0, 15 },
                        { 0, 0, 0, 2, 0, 0, 0, 8 },
                        { 0, 0, 0, 30, 0, 0, 0, 37 }
                };
                for (int position = 0; position < expected.length; position++) {
                    Assert.assertEquals(position, postings.nextPosition());
                    BytesRef payload = postings.getPayload();
                    Assert.assertArrayEquals(expected[position], Arrays.copyOfRange(
                            payload.bytes, payload.offset, payload.offset + payload.length));
                }

                Terms indexedTerms = MultiTerms.getTerms(reader, fieldName);
                Assert.assertFalse(indexedTerms.hasPositions());
                Assert.assertFalse(indexedTerms.hasPayloads());

                Assert.assertEquals(starts.length + 1, reader.storedFields().document(0)
                        .getField(AnnotatedFieldNameUtil.lengthTokensField(annotatedFieldName)).numericValue().intValue());
                String mainFieldName = AnnotatedFieldNameUtil.annotationField(annotatedFieldName, "word",
                        field.mainAnnotation().mainSensitivity());
                Assert.assertFalse(reader.getTermVector(0, mainFieldName).hasOffsets());
            }
        }
    }

    @Test
    public void testEmptyFieldOmitsSourceRanges() {
        DocWriter docWriter = Mockito.mock(DocWriter.class);
        Mockito.when(docWriter.indexObjectFactory()).thenReturn(BLIndexObjectFactoryLucene.INSTANCE);
        AnnotatedFieldWriter field = new AnnotatedFieldWriter(docWriter, "contents", "word",
                AnnotationSensitivities.ONLY_INSENSITIVE,
                AnnotatedFieldWriter.TokenOffsetStorage.SOURCE_RANGE_VECTORS, false, false, null);
        field.mainAnnotation().addValue("");
        field.addFinalStartEndChars();
        BLInputDocumentLucene document = new BLInputDocumentLucene(BLInputDocument.DocType.DOCUMENT);
        field.addToDoc(document);

        Assert.assertNull(document.getDocument().getField(AnnotatedFieldNameUtil.sourceRangesField("contents")));
        Assert.assertEquals(1, document.getDocument().getField(AnnotatedFieldNameUtil.lengthTokensField("contents"))
                .numericValue().intValue());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUnsupportedBackendRejected() {
        DocWriter docWriter = Mockito.mock(DocWriter.class);
        Mockito.when(docWriter.indexObjectFactory()).thenReturn(Mockito.mock(BLIndexObjectFactory.class));
        new AnnotatedFieldWriter(docWriter, "contents", "word", AnnotationSensitivities.ONLY_INSENSITIVE,
                AnnotatedFieldWriter.TokenOffsetStorage.SOURCE_RANGE_VECTORS, false, false, null);
    }

    @Test(expected = IllegalStateException.class)
    public void testIncompleteRangeRejected() {
        DocWriter docWriter = Mockito.mock(DocWriter.class);
        Mockito.when(docWriter.indexObjectFactory()).thenReturn(BLIndexObjectFactoryLucene.INSTANCE);
        AnnotatedFieldWriter field = new AnnotatedFieldWriter(docWriter, "contents", "word",
                AnnotationSensitivities.ONLY_INSENSITIVE,
                AnnotatedFieldWriter.TokenOffsetStorage.SOURCE_RANGE_VECTORS, false, false, null);
        field.addStartChar(1);
        field.addToDoc(new BLInputDocumentLucene(BLInputDocument.DocType.DOCUMENT));
    }
}
