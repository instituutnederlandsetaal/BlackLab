package nl.inl.blacklab.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

public class TestSingleDocIdFilter {

    @Test
    public void testEquals() {
        SingleDocIdFilter singleDocIdFilter = new SingleDocIdFilter(1);
        SingleDocIdFilter singleDocIdFilter2 = new SingleDocIdFilter(2);
        assertNotEquals(singleDocIdFilter, singleDocIdFilter2);
    }

    @Test
    public void testSelectsGlobalDocumentIdAcrossSegments() throws Exception {
        try (Directory directory = new ByteBuffersDirectory()) {
            IndexWriterConfig config = new IndexWriterConfig(new KeywordAnalyzer());
            config.setMergePolicy(NoMergePolicy.INSTANCE);
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (int segment = 0; segment < 2; segment++) {
                    writer.addDocument(new Document());
                    writer.addDocument(new Document());
                    writer.commit();
                }
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                assertEquals(2, reader.leaves().size());
                IndexSearcher searcher = new IndexSearcher(reader);
                for (int docId = 0; docId < reader.maxDoc(); docId++) {
                    var matches = searcher.search(new SingleDocIdFilter(docId), 1);
                    assertEquals(1, matches.totalHits.value);
                    assertEquals(docId, matches.scoreDocs[0].doc);
                }
                assertEquals(0, searcher.count(new SingleDocIdFilter(reader.maxDoc())));
            }
        }
    }
}
