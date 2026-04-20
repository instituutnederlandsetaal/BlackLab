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
import nl.inl.blacklab.server.lib.QueryParamsAbstract;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.webservice.WebserviceParameter;

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
        TestQueryParams paramsTrue = new TestQueryParams(Map.of(WebserviceParameter.TOKENIZED, "true"));
        TestQueryParams paramsFalse = new TestQueryParams(Map.of());
        Assert.assertTrue(paramsTrue.getAutocompleteTokenized());
        Assert.assertFalse(paramsFalse.getAutocompleteTokenized());
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

    private static class TestQueryParams extends QueryParamsAbstract {

        private final Map<WebserviceParameter, String> parameterValues;

        TestQueryParams(Map<WebserviceParameter, String> parameterValues) {
            super("test-index", Mockito.mock(SearchManager.class), User.anonymous("test"));
            this.parameterValues = parameterValues;
        }

        @Override
        protected boolean has(WebserviceParameter par) {
            return parameterValues.containsKey(par);
        }

        @Override
        protected String get(WebserviceParameter par) {
            return parameterValues.getOrDefault(par, WebserviceParameter.defaultString(par));
        }

        @Override
        public Map<WebserviceParameter, String> getParameters() {
            return parameterValues;
        }
    }
}
