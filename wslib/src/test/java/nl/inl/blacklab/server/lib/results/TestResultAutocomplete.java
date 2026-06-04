package nl.inl.blacklab.server.lib.results;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.lib.QueryParamsMap;
import nl.inl.blacklab.webservice.WsParam;

public class TestResultAutocomplete {

    @Test
    public void testFindMetadataFieldValuesByMatchingTokens() throws IOException {
        try (Directory directory = new ByteBuffersDirectory();
                DirectoryReader reader = createReader(directory)) {
            BlackLabIndex index = Mockito.mock(BlackLabIndex.class);
            Mockito.when(index.luceneDoc(Mockito.anyInt())).thenAnswer(
                    invocation -> reader.storedFields().document((int)invocation.getArgument(0), Set.of("author")));

            List<String> values = ResultAutocomplete.findMetadataFieldValuesByMatchingTokens(reader, index, "author",
                    List.of("smith"));
            Assert.assertEquals(List.of("Barton, Benjamin Smith", "Smith, Venture"), values);
        }
    }

    @Test
    public void testTokenizedAutocompleteParameterParsing() {
        Map<WsParam, String> parameterValues = Map.of();
        QueryParamsMap paramsDefault = new QueryParamsMap("test-index", parameterValues, null, null,
                Mockito.mock(BLSConfig.class), true);
        Assert.assertEquals("term", paramsDefault.get(WsParam.AUTOCOMPLETE_TYPE));
    }

    private static DirectoryReader createReader(Directory directory) throws IOException {
        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {
            writer.addDocument(authorDoc("Barton, Benjamin Smith"));
            writer.addDocument(authorDoc("Smith, Venture"));
            writer.addDocument(authorDoc("Jones, Amy"));
        }
        return DirectoryReader.open(directory);
    }

    private static Document authorDoc(String author) {
        Document doc = new Document();
        doc.add(new TextField("author", author, Field.Store.YES));
        return doc;
    }

}
