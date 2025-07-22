package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Collator;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFields;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
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
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.util.VersionFile;
import nl.inl.util.XmlHighlighter.UnbalancedTagsStrategy;

/** Interface for reading/searching a BlackLab index. For writing, see {@link BlackLabIndexWriter}. */
public interface BlackLabIndex extends AutoCloseable {

    String METADATA_FIELD_CONTENT_VIEWABLE = "contentViewable";

    default BLSpanQuery tagQuery(QueryInfo queryInfo, String luceneField, String tagNameRegex, Map<String, String> attributes,
            String captureAs) {
        return tagQuery(queryInfo, luceneField, tagNameRegex, attributes, TextPatternTags.Adjust.FULL_TAG, captureAs);
    }

    BLSpanQuery tagQuery(QueryInfo queryInfo, String luceneField, String tagNameRegex, Map<String, String> attributes,
            TextPatternTags.Adjust adjust, String captureAs);

    /** Get our index type (always integrated now). */
    IndexType getType();

    /** Get the strategy to use for indexing/searching relations. */
    RelationsStrategy getRelationsStrategy();

    enum IndexType {
        EXTERNAL_FILES, // (NO LONGER SUPPORTED) classic index with external forward index, etc.
        INTEGRATED,     // everything integrated into Lucene index
    }

    /**
     * Default number of context words to return around a hit.
     */
    ContextSize DEFAULT_CONTEXT_SIZE = ContextSize.get(5, Integer.MAX_VALUE);

    // Static [factory] methods
    //---------------------------------------------------------------
    
    /**
     * Does the specified directory contain a BlackLab index?
     *
     * NOTE: does NOT follow symlinks.
     * 
     * @param indexDir the directory
     * @return true if it's a BlackLab index, false if not.
     */
    static boolean isIndex(File indexDir) {
        try {
            if (VersionFile.exists(indexDir)) {
                VersionFile vf = VersionFile.read(indexDir);
                String version = vf.getVersion();
                return vf.getType().equals("blacklab") && (version.equals("1") || version.equals("2"));
            }

            // We also support integrated indexes which don't have a version file.
            // So just see if it's a Lucene index and assume it's a BlackLab index if so.
            // (Lucene index always has a segments_* file)
            try (Directory dir = FSDirectory.open(indexDir.toPath())){
                return DirectoryReader.indexExists(dir);
            }
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Does the specified directory contain a BlackLab index?
     *
     * NOTE: does NOT follow symlinks. Call {@link Path#toRealPath(LinkOption...)}
     * yourself if you want this.
     *
     * @param indexDirPath the directory
     * @return true if it's a BlackLab index, false if not.
     */
    static boolean isIndex(Path indexDirPath) {
        return isIndex(indexDirPath.toFile());
    }

    // Basic stuff, low-level access to index
    //---------------------------------------------------------------
    
    @Override
    boolean equals(Object obj);
    
    @Override
    int hashCode();
    
    /**
     * Finalize the index object. This closes the IndexSearcher and (depending on
     * the constructor used) may also close the index reader.
     */
    @Override
    void close();

    /**
     * Is this a newly created, empty index?
     * 
     * @return true if it is, false if not
     */
    boolean isEmpty();

    /**
     * Is the document id in range, and not a deleted document?
     * @param docId document id to check
     * @return true if it is an existing document
     */
    boolean docExists(int docId);

    /**
     * Perform a task on each (non-deleted) Lucene Document.
     * 
     * @param task task to perform
     */
    void forEachDocument(DocTask task);


    // Search the index
    //---------------------------------------------------------------------------
    
    /**
     * Find hits for a pattern in a field.
     * 
     * Uses the default search settings.
     *
     * @param query the pattern to find
     * @return the hits found
     */
    default Hits find(BLSpanQuery query) {
        return find(query, null);
    }

    /**
     * Find hits for a pattern in a field.
     *
     * @param queryInfo our query info
     * @param query the pattern to find
     * @param settings search settings, or null for default
     * @return the hits found
     */
    Hits find(QueryInfo queryInfo, BLSpanQuery query, SearchSettings settings);

    /**
     * Find hits for a pattern in a field.
     * 
     * @param query the pattern to find
     * @param settings search settings, or null for default
     * @return the hits found
     */
    default Hits find(BLSpanQuery query, SearchSettings settings) {
        return find(QueryInfo.create(this, fieldFromQuery(query), true), query, settings);
    }

    /**
     * Perform a document query only (no hits)
     * 
     * @param documentFilterQuery the document-level query
     * @return the matching documents
     */
    DocResults queryDocuments(Query documentFilterQuery);

    /**
     * Determine the term frequencies for an annotation sensitivity.
     * 
     * @param annotSensitivity the annotation + sensitivity indexing we want the term frequency for
     * @param filterQuery document filter, or null for all documents
     * @param terms a list of terms to retrieve frequencies for, or null/empty to retrieve frequencies for all terms
     * @return term frequencies
     */
    TermFrequencyList termFrequencies(AnnotationSensitivity annotSensitivity, Query filterQuery, Set<String> terms);

    /**
     * Explain how a SpanQuery is rewritten to an optimized version to be executed
     * by Lucene.
     *
     * @param query the query to explain
     * @return the explanation
     */
    QueryExplanation explain(BLSpanQuery query);
    
    /**
     * Start building a Search.
     *
     * @param field field to search
     * @param useCache whether to use the cache or bypass it
     * @return empty search object
     */
    SearchEmpty search(AnnotatedField field, boolean useCache);

    /**
     * Start building a Search. 
     * 
     * @param field field to search
     * @return empty search object
     */
    default SearchEmpty search(AnnotatedField field) {
        return search(field, true);
    }
    
    /**
     * Start building a Search.
     *
     * Uses the main annotated field, e.g. usually called "contents".
     *
     * @return empty search object
     */
    default SearchEmpty search() {
        return search(mainAnnotatedField());
    }

    
    // Access the different modules of the index
    //---------------------------------------------------------------------------
    
    /**
     * Get the Lucene index reader we're using.
     *
     * @return the Lucene index reader
     */
    IndexReader reader();

    IndexSearcher searcher();

    /**
     * Get the content accessor for a field.
     * 
     * @param field the field
     * @return the content accessor, or null if there is no content accessor for this field
     */
    ContentAccessor contentAccessor(Field field);

    /**
     * Tries to get the ForwardIndex object for the specified field name.
     *
     * Looks for an already-opened forward index first. If none is found, and if
     * we're in "create index" mode, may create a new forward index. Otherwise,
     * looks for an existing forward index and opens that.
     *
     * @param annotation the annotation for which we want the forward index
     * @return the ForwardIndex if found/created
     * @throws RuntimeException if the annotation has no forward index
     */
    AnnotationForwardIndex annotationForwardIndex(Annotation annotation);

    /**
     * Get forward index for the specified annotated field.
     * 
     * @param field field to get forward index for
     * @return forward index
     */
    ForwardIndex forwardIndex(AnnotatedField field);


    
    // Information about the index
    //---------------------------------------------------------------------------

    /**
     * Get the index directory.
     * For some implementations this may be null!
     * 
     * @return index directory
     */
    File indexDirectory();

    /**
     * Get information about the structure of the BlackLab index.
     *
     * @return the structure object
     */
    IndexMetadata metadata();

    /**
     * Get a field (either an annotated or a metadata field).
     * 
     * @param fieldName name of the field
     * @return the field
     */
    default Field field(String fieldName) {
        Field field = annotatedField(fieldName);
        if (field == null)
            field = metadataField(fieldName);
        return field;
    }

    default AnnotatedFields annotatedFields() {
        return metadata().annotatedFields();
    }

    default AnnotatedField annotatedField(String fieldName) {
        return metadata().annotatedField(fieldName);
    }

    default AnnotatedField fieldFromQuery(BLSpanQuery q) {
        return annotatedField(q.getField());
    }

    default AnnotatedField mainAnnotatedField() {
        return metadata().annotatedFields().main();
    }
    
    default MetadataFields metadataFields() {
        return metadata().metadataFields();
    }

    /**
     * Get the specified metadata field config.
     *
     * @param fieldName metadata field name
     * @return metadata field config
     * @throws IllegalArgumentException if field not found
     */
    default MetadataField metadataField(String fieldName) {
        return metadata().metadataField(fieldName);
    }

    
    // Get settings
    //---------------------------------------------------------------------------

    /**
     * The default settings for all new Hits objects.
     *
     * You may change these settings; this will affect all new Hits objects.
     *
     * @return settings object
     */
    SearchSettings searchSettings();

    /**
     * How do we fix well-formedness for snippets of XML?
     * 
     * @return the setting: either adding or removing unbalanced tags
     */
    UnbalancedTagsStrategy defaultUnbalancedTagsStrategy();

    MatchSensitivity defaultMatchSensitivity();

    /**
     * Get the default initial query execution context.
     *
     * @param field field to search
     * @return query execution context
     */
    QueryExecutionContext defaultExecutionContext(AnnotatedField field);

    /**
     * Get the collator being used for sorting.
     *
     * @return the collator
     */
    Collator collator();

    /**
     * Get the analyzer for indexing and searching.
     * 
     * @return the analyzer
     */
    Analyzer analyzer();
    
    /**
     * Get the default context size.
     * @return default context size
     */
    ContextSize defaultContextSize();

    /**
     * Are we running in index mode?
     * @return true if we are, false if not
     */
    boolean indexMode();


    // Methods that mutate settings
    //---------------------------------------------------------------------------

    /**
     * Set the collator used for sorting.
     *
     * The default collator is for English.
     *
     * @param collator the collator
     */
    void setCollator(Collator collator);

    /**
     * Set the default sensitivity for queries.
     * @param m default match sensitivity
     */
    void setDefaultMatchSensitivity(MatchSensitivity m);

    /**
     * Set the maximum number of hits to process/count.
     * @param settings desired settings
     */
    void setSearchSettings(SearchSettings settings);
    
    /**
     * Set the default context size.
     * @param size default context size
     */
    void setDefaultContextSize(ContextSize size);

    /**
     * Set the object BlackLab should use as cache.
     * 
     * BlackLab will notify the cache of search results and will ask
     * the cache for results before executing a search.
     * 
     * It is up to the application to implement an effective cache, deciding
     * whether to cache a result and ensuring the cache doesn't grow too large.
     * 
     * @param cache cache object to use
     */
    void setCache(SearchCache cache);

    SearchCache cache();

    /**
     * Get the BlackLab instance that created us.
     * @return BlackLab instance
     */
    BlackLabEngine blackLab();

    /**
     * Get the Lucene document from the index.
     *
     * Skips content store fields.
     *
     * @param docId document id
     * @return Lucene document
     */
    default Document luceneDoc(int docId) {
        return luceneDoc(docId, false);
    }

    /**
     * Get the Lucene document from the index.
     *
     * @param docId document id
     * @param includeContentStores include content store fields or not?
     * @return Lucene document
     */
    Document luceneDoc(int docId, boolean includeContentStores);

    /**
     * Return a query matching all "real" documents in the index.
     *
     * This excludes the index metadata document or other such administrative
     * documents if they exist.
     *
     * In practice, this will probably return only documents that have a value
     * for the main annotated field; that's usually the same, but we should fix
     * this anyway.
     *
     * @return query matching all documents
     */
    Query getAllRealDocsQuery();

    ForwardIndexAccessor forwardIndexAccessor(String searchField);

    /**
     * a document may be viewed when a contentViewable metadata field with a value
     * true is registered with either the document or with the index metadata.
     *
     * @param document document we want to view
     * @return true iff the content from documents in the index may be viewed
     */
    default boolean mayView(Document document) {
        if (metadata().metadataFields().exists(METADATA_FIELD_CONTENT_VIEWABLE))
            return Boolean.parseBoolean(document.get(METADATA_FIELD_CONTENT_VIEWABLE));
        return metadata().contentViewable();
    }
    
    String name();

    RelationsStats getRelationsStats(AnnotatedField field, long limitValues);
}
