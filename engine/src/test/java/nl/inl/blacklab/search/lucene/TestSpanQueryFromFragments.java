package nl.inl.blacklab.search.lucene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.queries.spans.SpanWeight;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.BLInputDocumentLucene;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.QueryInfo;

public class TestSpanQueryFromFragments {

    @Test
    public void testIndependentIteratorsWithAndWithoutFragments() throws Exception {
        try (Directory directory = new ByteBuffersDirectory()) {
            AnnotatedField field = mock(AnnotatedField.class);
            when(field.name()).thenReturn("contents");
            when(field.tokenLengthField()).thenReturn(AnnotatedFieldNameUtil.lengthTokensField("contents"));
            QueryInfo queryInfo = QueryInfo.create(mock(BlackLabIndex.class), field);
            IndexWriterConfig config = new IndexWriterConfig(new KeywordAnalyzer());
            config.setMergePolicy(NoMergePolicy.INSTANCE);
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                BLInputDocumentLucene plain = parent(queryInfo, 3);
                writer.addDocument(plain.getDocument());
                writer.commit();
                BLInputDocumentLucene fragment = new BLInputDocumentLucene(BLInputDocument.DocType.FRAGMENT);
                fragment.addIndexedAndDocValues(BLInputDocument.FRAG_FIELD_ANNOTATED_FIELD, queryInfo.field().name());
                fragment.addNumericField(BLInputDocument.FRAG_FIELD_START, 1, false, false, true);
                fragment.addNumericField(BLInputDocument.FRAG_FIELD_END, 2, false, false, true);
                writer.addDocuments(List.of(fragment.getDocument(), parent(queryInfo, 4).getDocument()));
                writer.addDocument(parent(queryInfo, 2).getDocument());
                writer.commit();
                writer.addDocument(new BLInputDocumentLucene(BLInputDocument.DocType.INDEXMETADATA).getDocument());
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                assertEquals(3, reader.leaves().size());
                var query = new SpanQueryFromFragments(queryInfo, new MatchAllDocsQuery(), null,
                        SpanQueryFromFragments.Behaviour.PREFER_FULL_DOCS);
                var weight = query.createWeight(new IndexSearcher(reader), ScoreMode.COMPLETE_NO_SCORES, 1);
                BLSpans plain = weight.getSpans(reader.leaves().get(0), SpanWeight.Postings.POSITIONS);
                BLSpans withFragment = weight.getSpans(reader.leaves().get(1), SpanWeight.Postings.POSITIONS);
                BLSpans secondPlain = weight.getSpans(reader.leaves().get(0), SpanWeight.Postings.POSITIONS);
                assertSpan(withFragment, 1, 0, 4);
                assertSpan(withFragment, 2, 0, 2);
                assertEquals(BLSpans.NO_MORE_DOCS, withFragment.nextDoc());
                assertSpan(plain, 0, 0, 3);
                assertEquals(BLSpans.NO_MORE_DOCS, plain.nextDoc());
                assertSpan(secondPlain, 0, 0, 3);
                assertEquals(BLSpans.NO_MORE_DOCS, secondPlain.nextDoc());
                assertNull(weight.getSpans(reader.leaves().get(2), SpanWeight.Postings.POSITIONS));
            }
        }
    }

    private static BLInputDocumentLucene parent(QueryInfo queryInfo, int length) {
        BLInputDocumentLucene doc = new BLInputDocumentLucene(BLInputDocument.DocType.DOCUMENT);
        doc.addNumericField(queryInfo.field().tokenLengthField(),
                length + BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN, false, false, true);
        return doc;
    }

    private static void assertSpan(BLSpans spans, int docId, int start, int end) throws Exception {
        assertEquals(docId, spans.nextDoc());
        assertEquals(-1, spans.startPosition());
        assertEquals(-1, spans.endPosition());
        assertEquals(start, spans.nextStartPosition());
        assertEquals(end, spans.endPosition());
        assertEquals(BLSpans.NO_MORE_POSITIONS, spans.nextStartPosition());
    }
}
