package nl.inl.blacklab.mocks;

import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.analysis.BuiltinAnalyzers;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabEngine;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ContentAccessor;
import nl.inl.blacklab.search.DocTask;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.RelationsStats;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.textpattern.TextPatternTags;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.searches.SearchCacheDummy;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.util.XmlHighlighter.UnbalancedTagsStrategy;

public class MockBlackLabIndex implements BlackLabIndex {

    private final IndexMetadata indexMetadata;

    private final SearchSettings searchSettings;

    private final Map<Annotation, AnnotationForwardIndex> forwardIndices = new HashMap<>();

    private final Analyzer analyzer;

    private IndexSearcher searcher;

    private final SearchCache cache = new SearchCacheDummy();

    private final BlackLabEngine blackLab;

    public MockBlackLabIndex() {
        super();
        indexMetadata = new MockIndexMetadata();
        analyzer = BuiltinAnalyzers.STANDARD.getAnalyzer();
        searchSettings = SearchSettings.defaults();

        // Register ourselves in the mapping from IndexReader to BlackLabIndex,
        // so we can find the corresponding BlackLabIndex object from within Lucene code
        blackLab = BlackLab.implicitInstance();
        blackLab.registerIndex(null, this);
    }
    
    public QueryInfo createDefaultQueryInfo() {
        return QueryInfo.create(this, mainAnnotatedField());
    }

    @Override
    public void close() {
        blackLab.removeIndex(this);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean docExists(int doc) {
        return true;
    }

    @Override
    public IndexReader reader() {
        return null; // used by Hits
    }

    @Override
    public QueryExecutionContext defaultExecutionContext(AnnotatedField field) {
        return QueryExecutionContext.get(this, field.mainAnnotation(), MatchSensitivity.INSENSITIVE);
    }

    @Override
    public File indexDirectory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocResults queryDocuments(Query documentFilterQuery) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexSearcher searcher() {
        return searcher;
    }
    
    public void setIndexSearcher(IndexSearcher searcher) {
        this.searcher = searcher;
    }

    public void setForwardIndex(Annotation fieldPropName, AnnotationForwardIndex forwardIndex) {
        forwardIndices.put(fieldPropName, forwardIndex);
    }

    @Override
    public SearchSettings searchSettings() {
        return searchSettings;
    }

    @Override
    public UnbalancedTagsStrategy defaultUnbalancedTagsStrategy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCollator(Collator collator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collator collator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexMetadata metadata() {
        return indexMetadata;
    }

    @Override
    public void forEachDocument(DocTask task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Hits find(QueryInfo queryInfo, BLSpanQuery query, SearchSettings settings) throws TooManyClauses {
        throw new UnsupportedOperationException();
    }

    @Override
    public QueryExplanation explain(BLSpanQuery query) throws TooManyClauses {
        throw new UnsupportedOperationException();
    }

    @Override
    public ContentAccessor contentAccessor(Field field) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AnnotationForwardIndex annotationForwardIndex(Annotation annotation) {
        return forwardIndices.get(annotation);
    }

    @Override
    public ForwardIndex forwardIndex(AnnotatedField field) {
        return null;
    }

    @Override
    public MatchSensitivity defaultMatchSensitivity() {
        return MatchSensitivity.CASE_INSENSITIVE;
    }

    @Override
    public void setDefaultMatchSensitivity(MatchSensitivity m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Analyzer analyzer() {
        return analyzer;
    }

    @Override
    public void setSearchSettings(SearchSettings settings) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDefaultContextSize(ContextSize size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ContextSize defaultContextSize() {
        return ContextSize.get(5, Integer.MAX_VALUE);
    }

    @Override
    public boolean indexMode() {
        return false;
    }

    @Override
    public TermFrequencyList termFrequencies(AnnotationSensitivity annotSensitivity, Query filterQuery, Set<String> terms) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchEmpty search(AnnotatedField field, boolean useCache) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void setCache(SearchCache cache) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchCache cache() {
        return cache;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "()";
    }
    
    @Override
    public BlackLabEngine blackLab() {
        return blackLab;
    }

    @Override
    public ForwardIndexAccessor forwardIndexAccessor(String searchField) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Query getAllRealDocsQuery() {
        return new MatchAllDocsQuery();
    }

    @Override
    public Document luceneDoc(int docId, boolean includeContentStores) {
        if (includeContentStores)
            throw new UnsupportedOperationException("Always skips content stores");
        try {
            return reader().document(docId);
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    @Override
    public BLSpanQuery tagQuery(QueryInfo queryInfo, String luceneField, String tagNameRegex,
            Map<String, String> attributes, TextPatternTags.Adjust adjust, String captureAs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexType getType() {
        return IndexType.INTEGRATED;
    }

    @Override
    public String name() { 
        return "MockBlackLabIndex";
    }

    @Override
    public RelationsStats getRelationsStats(AnnotatedField field, long limitValues) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RelationsStrategy getRelationsStrategy() {
        throw new UnsupportedOperationException();
    }
}
