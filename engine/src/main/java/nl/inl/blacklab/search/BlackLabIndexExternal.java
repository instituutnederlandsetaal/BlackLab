package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.contentstore.ContentStoreExternal;
import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndexExternalWriter;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexAbstract;
import nl.inl.blacklab.forwardindex.ForwardIndexExternal;
import nl.inl.blacklab.index.BLIndexWriterProxyLucene;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorExternal;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataExternal;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategyNaiveSeparateTerms;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureGroup;
import nl.inl.blacklab.search.lucene.SpanQueryEdge;
import nl.inl.blacklab.search.lucene.SpanQueryTagsExternal;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPatternTags;
import nl.inl.util.VersionFile;

/**
 * A legacy BlackLab index with external forward index, etc.
 */
public class BlackLabIndexExternal extends BlackLabIndexAbstract {

    BlackLabIndexExternal(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex,
            ConfigInputFormat config) throws ErrorOpeningIndex {
        super(indexDir.getName(), blackLab, null, indexDir, indexMode, createNewIndex, config, null);
    }

    BlackLabIndexExternal(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex,
            File indexTemplateFile) throws ErrorOpeningIndex {
        super(indexDir.getName(), blackLab, null, indexDir, indexMode, createNewIndex, null, indexTemplateFile);
    }

    /**
     * If this directory contains any external index files/subdirs, delete them.
     *
     * Doesn't delete the Lucene index (Lucene does this when creating a new index in a dir).
     *
     * @param indexDir the directory to clean up
     */
    public static void deleteOldIndexFiles(File indexDir) {
        if (VersionFile.exists(indexDir)) {
            for (File f: indexDir.listFiles()) {
                if (f.getName().equals(VersionFile.FILE_NAME)) {
                    if (!f.delete())
                        logger.warn("Could not delete version file " + f);
                } else if (f.getName().matches("(fi|cs)_.+|indexmetadata\\.(ya?ml|json)")) {
                    try {
                        if (f.isDirectory())
                            FileUtils.deleteDirectory(f);
                        else if (!f.delete())
                            logger.warn("Could not delete index metadata file: " + f);
                    } catch (IOException e) {
                        logger.warn("Could not delete subdirectory " + f);
                    }
                }
            }
        }
    }

    @Override
    protected IndexMetadataWriter getIndexMetadata(boolean createNewIndex, ConfigInputFormat config)
            throws IndexVersionMismatch {
        return new IndexMetadataExternal(this, indexDirectory(), createNewIndex, config);
    }

    @Override
    protected IndexMetadataWriter getIndexMetadata(boolean createNewIndex, File indexTemplateFile)
            throws IndexVersionMismatch {
        return new IndexMetadataExternal(this, indexDirectory(), createNewIndex, indexTemplateFile);
    }

    public ForwardIndex createForwardIndex(AnnotatedField field) {
        return new ForwardIndexExternal(this, field);
    }

    @Override
    protected void deleteFromForwardIndices(Document d) {
        // Delete this document in all forward indices
        for (Map.Entry<AnnotatedField, ForwardIndex> e: forwardIndices.entrySet()) {
            AnnotatedField field = e.getKey();
            ForwardIndex fi = e.getValue();
            for (Annotation annotation: field.annotations()) {
                if (annotation.hasForwardIndex())
                    ((AnnotationForwardIndexExternalWriter)fi.get(annotation)).deleteDocumentByLuceneDoc(d);
            }
        }
    }

    @Override
    protected void checkCanOpenIndex(boolean createNewIndex) throws IllegalArgumentException {
        // If there's a version file (non-integrated index), check it now.
        File indexLocation = indexDirectory();
        if (!createNewIndex) {
            if (!indexMode() || VersionFile.exists(indexLocation)) {
                if (!BlackLabIndex.isIndex(indexLocation)) {
                    throw new IllegalArgumentException("Not a BlackLab index, or wrong version! "
                            + VersionFile.report(indexLocation));
                }
            }
        }
    }

    @Override
    protected IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer useAnalyzer) throws IOException {
        IndexWriter writer = super.openIndexWriter(indexDir, create, useAnalyzer);

        // Create or check for the version file
        if (create) {
            // New index. Create our version file.
            VersionFile.write(indexDir, "blacklab", "2");
        } else {
            // Existing index. Should already have a version file.
            if (!BlackLabIndex.isIndex(indexDir)) {
                throw new IllegalArgumentException("Not a BlackLab index, or wrong type or version! "
                        + VersionFile.report(indexDir) + ": " + indexDir);
            }
        }
        return writer;
    }

    @Override
    protected void openContentStore(Field field, boolean createNewContentStore, File indexDir) {
        // Classic external index format. Open external content store.
        File dir = new File(indexDir, "cs_" + field.name());
        if (!dir.exists() && !createNewContentStore) {
            throw new IllegalStateException("Field " + field.name() +
                    " should have content store, but directory " + dir + " not found!");
        }
        if (traceIndexOpening())
            logger.debug("    " + dir + "...");
        ContentStore cs = ContentStoreExternal.open(dir, indexMode(), createNewContentStore);
        registerContentStore(field, cs);
    }

    @Override
    public void close() {
        // Close the (external) forward indices
        for (ForwardIndex fi: forwardIndices.values()) {
            ((ForwardIndexAbstract)fi).close();
        }

        // Note that we call super.close() AFTER closing the forward indexes, or our index will momentarily
        // seem to be "finished" will actually being in an invalid state.
        super.close();
    }

    @Override
    public ForwardIndexAccessor forwardIndexAccessor(String searchField) {
        return new ForwardIndexAccessorExternal(this, annotatedField(searchField));
    }

    @Override
    public BLSpanQuery tagQuery(QueryInfo queryInfo, String luceneField, String tagNameRegex,
            Map<String, String> attributes, TextPatternTags.Adjust adjust, String captureAs) {
        BLSpanQuery q = new SpanQueryTagsExternal(queryInfo, luceneField, tagNameRegex, attributes);
        if (adjust == TextPatternTags.Adjust.LEADING_EDGE || adjust == TextPatternTags.Adjust.TRAILING_EDGE)
            q = new SpanQueryEdge(q, adjust == TextPatternTags.Adjust.TRAILING_EDGE);
        if (!StringUtils.isEmpty(captureAs))
            q = new SpanQueryCaptureGroup(q, captureAs, 0, 0, captureAs);
        return q;
    }

    @Override
    public IndexType getType() {
        return IndexType.EXTERNAL_FILES;
    }

    @Override
    public Query getAllRealDocsQuery() {
        return new MatchAllDocsQuery(); // there are no non-real documents in this index type
    }

    @Override
    public boolean needsPrimaryValuePayloads() {
        return false;
    }

    @Override
    public Document luceneDoc(int docId, boolean includeContentStores) {
        if (includeContentStores)
            throw new UnsupportedOperationException("External index format always skips content stores");
        try {
            return reader().document(docId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(Query q) {
        logger.debug("Delete query: " + q);
        if (!indexMode())
            throw new RuntimeException("Cannot delete documents, not in index mode");
        try {
            // We have to delete the document from the CS and FI separately.

            // Open a fresh reader to execute the query
            IndexWriter luceneIndexWriter = ((BLIndexWriterProxyLucene)indexWriter).getWriter();
            try (IndexReader freshReader = DirectoryReader.open(luceneIndexWriter, false,
                    false)) {
                // Execute the query, iterate over the docs and delete from FI and CS.
                IndexSearcher s = new IndexSearcher(freshReader);
                Weight w = s.createWeight(q, ScoreMode.COMPLETE_NO_SCORES, 1.0f);
                logger.debug("Doing delete. Number of leaves: " + freshReader.leaves().size());
                for (LeafReaderContext leafContext: freshReader.leaves()) {
                    Bits liveDocs = leafContext.reader().getLiveDocs();

                    Scorer scorer = w.scorer(leafContext);
                    if (scorer == null) {
                        logger.debug("  No hits in leafcontext");
                        continue; // no matching documents
                    }

                    // Iterate over matching docs
                    DocIdSetIterator it = scorer.iterator();
                    logger.debug("  Iterate over matching docs in leaf");
                    while (true) {
                        int docId = it.nextDoc();
                        if (docId == DocIdSetIterator.NO_MORE_DOCS)
                            break;
                        if (liveDocs != null && !liveDocs.get(docId)) {
                            // already deleted.
                            continue;
                        }
                        docId += leafContext.docBase;
                        Document d = freshReader.document(docId);
                        logger.debug(
                                "    About to delete docId " + docId + ", fromInputFile=" + d.get("fromInputFile")
                                        + " from FI and CS");

                        deleteFromForwardIndices(d);

                        // Delete this document in all content stores
                        contentStores.deleteDocument(d);
                    }
                }
            }

            // Finally, delete the documents from the Lucene index
            logger.debug("  Delete docs from Lucene index");
            indexWriter.deleteDocuments(q);

        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @Override
    public RelationsStrategy getRelationsStrategy() {
        return RelationsStrategyNaiveSeparateTerms.INSTANCE;
    }
}
