package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.Term;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.contentstore.ContentStoreExternal;
import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.DocumentFormatNotFound;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexExternal;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.indexers.config.WarnOnce;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldsImpl;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.util.FileProcessor;
import nl.inl.util.FileReference;
import nl.inl.util.FileUtil;
import nl.inl.util.TextContent;

/**
 * Tool for indexing. Reports its progress to an IndexListener.
 *
 * Not thread-safe, although indexing itself can use thread in certain cases
 * (only when using configuration file based indexing right now)
 */
@NotThreadSafe // in index mode
class IndexerImpl implements DocWriter, Indexer {

    static final Logger logger = LogManager.getLogger(IndexerImpl.class);

    /**
     * FileProcessor FileHandler that creates a DocIndexer for every file and
     * performs some reporting.
     */
    private class DocIndexerWrapper implements FileProcessor.FileHandler {

        @Override
        public void file(FileReference file) throws MalformedInputFile, PluginException {
            InputFormat inputFormat = DocumentFormats.getFormat(IndexerImpl.this.formatIdentifier).orElseThrow();
            try (DocIndexer docIndexer = inputFormat.createDocIndexer(IndexerImpl.this, file)) {
                if (docIndexer == null) {
                    throw new PluginException(
                            "Could not instantiate DocIndexer: " + IndexerImpl.this.formatIdentifier + ", " + file.getPath());
                }
                if (file.getAssociatedFile() != null)
                    docIndexer.setDocumentDirectory(file.getAssociatedFile().getParentFile()); // for XInclude resolution

                if (docIndexer.continueIndexing()) {
                    listener().fileStarted(file.getPath());
                    int docsDoneBefore = docIndexer.numberOfDocsDone();
                    long tokensDoneBefore = docIndexer.numberOfTokensDone();
                    try {
                        docIndexer.index();
                    } catch (Exception e) {
                        throw new ErrorIndexingFile("Error while indexing input file: " + file.getPath(), e);
                    }
                    listener().fileDone(file.getPath());

                    int docsDoneAfter = docIndexer.numberOfDocsDone();
                    if (docsDoneAfter == docsDoneBefore) {
                        logger.warn("No docs found in " + file.getPath() + "; wrong format?");
                    }
                    long tokensDoneAfter = docIndexer.numberOfTokensDone();
                    if (tokensDoneAfter == tokensDoneBefore) {
                        logger.warn("No words indexed in " + file.getPath() + "; wrong format?");
                    }
                }
            }
        }

        @Override
        public void directory(File dir) {
            // ignore
        }
    }

    private final DocIndexerWrapper docIndexerWrapper = new DocIndexerWrapper();

    /** Our index */
    private BlackLabIndexWriter indexWriter;

    /** Stop after indexing this number of docs. -1 if we shouldn't stop. */
    private int maxNumberOfDocsToIndex = -1;

    /**
     * Where to report indexing progress.
     */
    private IndexListener listener = null;

    /**
     * Have we reported our creation and the start of indexing to the listener yet?
     */
    private boolean createAndIndexStartReported = false;

    /**
     * When we encounter a zip or tgz file, do we descend into it like it was a
     * directory?
     */
    private boolean processArchivesAsDirectories = true;

    /**
     * Recursively index files inside a directory? (or archive file, if
     * processArchivesAsDirectories == true)
     */
    private boolean defaultRecurseSubdirs = true;

    /**
     * Format of the documents we're going to be indexing, used to create the
     * correct type of DocIndexer.
     */
    private String formatIdentifier;

    /**
     * Parameters we should pass to our DocIndexers upon instantiation.
     */
    private Map<String, String> indexerParam;

    /** How to index metadata fields (tokenized) */
    private BLFieldType metadataFieldTypeTokenized;

    /** How to index metadata fields (untokenized) */
    private BLFieldType metadataFieldTypeUntokenized;

    /** Where to look for files linked from the input files */
    private final List<File> linkedFileDirs = new ArrayList<>();

    /**
     * If a file cannot be found in the linkedFileDirs, use this to retrieve it (if
     * present)
     */
    private Function<String, File> linkedFileResolver;

    /** Index using multiple threads or just one? */
    private int numberOfThreadsToUse = 1;

    // TODO this is a workaround for a bug where indexMetadata is always written, even when an indexing task was
    //   rollbacked on an empty index. Result of this is that the index can never be opened again (the forwardindex
    //   is missing files that the indexMetadata.yaml says must exist?) so record rollbacks and then don't write
    //   the updated indexMetadata
    private boolean hasRollback = false;

    /** Was this Indexer closed? */
    private boolean closed = false;

    /** To ensure certain warnings are only issued once */
    WarnOnce warnOnce = new WarnOnce(logger);

    /**
     * Construct Indexer
     *
     * @param directory the main BlackLab index directory
     * @param create if true, creates a new index; otherwise, appends to existing
     *            index. When creating a new index, a formatIdentifier or an
     *            indexTemplateFile containing a valid "documentFormat" value should
     *            also be supplied. Otherwise adding new data to the index isn't
     *            possible, as we can't construct a DocIndexer to do the actual
     *            indexing without a valid formatIdentifier.
     * @param formatIdentifier (optional) determines how this Indexer will index any
     *            new data added to it. If omitted, when opening an existing index,
     *            the formatIdentifier in its metadata (as "documentFormat") is used
     *            instead. When creating a new index, this format will be stored as
     *            the default for that index, unless another default is already set
     *            by the indexTemplateFile (as "documentFormat"), it will still be
     *            used by this Indexer however.
     * @param indexTemplateFile (optional) JSON file to use as template for index structure /
     *            metadata (if creating new index)
     * @throws DocumentFormatNotFound if no formatIdentifier was specified and
     *             autodetection failed
     * @deprecated use {@link IndexerImpl(BlackLabIndexWriter, String, File)} with
     *   {@link BlackLab#openForWriting(File, boolean, String, File)} instead
     */
    @Deprecated
    IndexerImpl(File directory, boolean create, String formatIdentifier, File indexTemplateFile)
            throws DocumentFormatNotFound, ErrorOpeningIndex {
        BlackLabIndexWriter indexWriter = BlackLab.openForWriting(directory, create, formatIdentifier,
                indexTemplateFile);
        init(indexWriter, formatIdentifier);
    }

    /**
     * Open an indexer for the provided writer.
     *
     * @param writer the writer
     * @param formatIdentifier (optional) - the formatIdentifier to use when indexing data through this indexer.
     *      If omitted, uses the default formatIdentifier stored in the indexMetadata. If that is missing too, throws DocumentFormatNotFound.
     */
    IndexerImpl(BlackLabIndexWriter writer, String formatIdentifier) throws DocumentFormatNotFound {
        init(writer, formatIdentifier);
    }

    private void init(BlackLabIndexWriter indexWriter, String formatIdentifier) throws DocumentFormatNotFound {
        if (indexWriter == null) {
            throw new IllegalStateException("indexWriter == null");
        }

        this.indexWriter = indexWriter;

        // Make sure we have a supported format, and make sure a default format is recorded in the metadata.
        try {
            this.formatIdentifier = determineFormat(indexWriter.name(), formatIdentifier, indexWriter.metadata().documentFormat());
            BlackLabIndexWriter.setMetadataDocumentFormatIfMissing(indexWriter, formatIdentifier);
        } catch (DocumentFormatNotFound e) {
            indexWriter.close();
            throw e;
        }

        initMetadataFieldTypes();
    }

    /**
     * Determine what format to use, the specified or the default one.
     *
     * Will return a supported format, preferring the specified one to the
     * default, or throw an exception.
     *
     * @param indexName for exception message
     * @param formatIdentifier specified format
     * @param fallbackFormat default to fall back if the specified format is not supported
     * @return chosen format
     * @throws DocumentFormatNotFound if neither format is supported
     */
    private String determineFormat(String indexName, String formatIdentifier, String fallbackFormat)
            throws DocumentFormatNotFound {
        if (formatIdentifier == null || !DocumentFormats.isSupported(formatIdentifier)) {
            // Specified format not found; use index default
            if (fallbackFormat == null || !DocumentFormats.isSupported(fallbackFormat)) {
                // Index default doesn't work either, error
                throw new DocumentFormatNotFound(
                        "Could not determine documentFormat for index " + indexName + " (" + formatIdentifier
                                + (fallbackFormat == null ? "" : " / " + fallbackFormat) + "): " + formatError(fallbackFormat));
            }
            formatIdentifier = fallbackFormat;
        }
        return formatIdentifier;
    }

    private void initMetadataFieldTypes() {
        metadataFieldTypeTokenized = indexWriter.indexObjectFactory().fieldTypeMetadata(true);
        metadataFieldTypeUntokenized = indexWriter.indexObjectFactory().fieldTypeMetadata(false);
    }

    private String formatError(String formatIdentifier) {
        String formatError = null;
        if (formatIdentifier == null)
            formatError = "No formatIdentifier";
        else {
            Optional<InputFormat> inputFormat = DocumentFormats.getFormatOrError(formatIdentifier);
            if (!inputFormat.isPresent())
                formatError =  "Unknown formatIdentifier '" + formatIdentifier + "'";
            else if (inputFormat.get().isError())
                formatError = ((InputFormatError)inputFormat.get()).getErrorMessage();
        }
        return formatError;
    }

    @Override
    public BLFieldType metadataFieldType(boolean tokenized) {
        return tokenized ? metadataFieldTypeTokenized : metadataFieldTypeUntokenized;
    }

    @Override
    public void setProcessArchivesAsDirectories(boolean b) {
        processArchivesAsDirectories = b;
    }

    @Override
    public void setRecurseSubdirs(boolean recurseSubdirs) {
        this.defaultRecurseSubdirs = recurseSubdirs;
    }

    @Override
    public void setFormatIdentifier(String formatIdentifier) throws DocumentFormatNotFound {
        if (!DocumentFormats.isSupported(formatIdentifier))
            throw new DocumentFormatNotFound("Cannot set formatIdentifier '" + formatIdentifier + "' for index "
                    + this.indexWriter.name() + "; " + formatError(formatIdentifier));

        this.formatIdentifier = formatIdentifier;
    }

    @Override
    public synchronized void setListener(IndexListener listener) {
        this.listener = listener;
        listener(); // report creation and start of indexing, if it hadn't been reported yet
    }

    @Override
    public synchronized IndexListener listener() {
        if (listener == null) {
            listener = new IndexListenerReportConsole();
        }
        if (!createAndIndexStartReported) {
            createAndIndexStartReported = true;
            listener.indexerCreated(this);
            listener.indexStart();
        }
        return listener;
    }

    /**
     * Log an exception that occurred during indexing
     *
     * @param msg log message
     * @param e the exception
     */
    protected void log(String msg, Exception e) {
        logger.error(msg, e);
    }

    @Override
    public void setMaxNumberOfDocsToIndex(int n) {
        this.maxNumberOfDocsToIndex = n;
    }

    @Override
    public void rollback() {
        listener().rollbackStart();
        indexWriter.rollback();
        listener().rollbackEnd();
        hasRollback = true;
    }

    // FIXME this should call close() on running FileProcessors
    @Override
    public synchronized void close() {

        // Signal to the listener that we're done indexing and closing the index (which might take a
        // while)
        listener().indexEnd();
        listener().closeStart();

        if (!hasRollback) {
            indexWriter.metadata().addToTokenCount(listener().getTokensProcessed());
            indexWriter.metadata().save();
        }
        indexWriter.close();

        // Signal that we're completely done now
        listener().closeEnd();
        listener().indexerClosed();

        closed = true;
    }

    @Override
    public boolean isOpen() {
        return !closed && indexWriter.isOpen();
    }

    /**
     * Add a Lucene document to the index
     *
     * @param document the document to add
     */
    @Override
    public void add(BLInputDocument document) throws IOException {
        indexWriter.addDocument(document);
        listener().documentAddedToIndex();
    }

    @Override
    public void update(Term term, BLInputDocument document) throws IOException {
        indexWriter.updateDocument(term, document);
        listener().documentAddedToIndex();
    }

    @Override
    public void addToForwardIndex(AnnotatedFieldWriter fieldWriter, BLInputDocument currentDoc) {
        if (getIndexType() == BlackLabIndex.IndexType.EXTERNAL_FILES) {
            // External forward index: add to it (with the integrated forward index, this is handled in the codec)
            ForwardIndex fi = indexWriter().forwardIndex(fieldWriter.field());
            Map<Annotation, List<String>> annotations = new HashMap<>();
            Map<Annotation, List<Integer>> posIncr = new HashMap<>();
            for (AnnotationWriter annotationWriter: fieldWriter.annotationWriters()) {
                if (annotationWriter.hasForwardIndex()) {
                    Annotation annotation = annotationWriter.annotation();
                    annotations.put(annotation, annotationWriter.values());
                    posIncr.put(annotation, annotationWriter.positionIncrements());
                }
            }
            ((ForwardIndexExternal) fi).addDocument(annotations, posIncr, ((BLInputDocumentLucene) currentDoc).getDocument());
        }
    }

    @Override
    public void storeInContentStore(BLInputDocument currentDoc, TextContent document, String contentIdFieldName,
            String contentStoreName) {

        if (getIndexType() == BlackLabIndex.IndexType.INTEGRATED) {
            // Integrated index: store as a field in the document
            AnnotatedFieldsImpl annotatedFields = indexWriter.metadata().annotatedFields();
            if (annotatedFields.exists(contentStoreName)) {
                annotatedFields.get(contentStoreName).setContentStore(true);
            }

            String luceneFieldName = AnnotatedFieldNameUtil.contentStoreField(contentStoreName);
            BLFieldType fieldType = indexWriter.indexObjectFactory().fieldTypeContentStore();

            currentDoc.addField(luceneFieldName, document.toString(), fieldType);

        } else {
            Field field = indexWriter.metadata().annotatedField(contentStoreName);
            if (field == null)
                field = indexWriter.metadata().metadataField(contentStoreName);

            // TODO move store function into ContentStore
            // this will require moving ContentStore into engine module so it can see BlInputDocument class.
            ContentStore store = indexWriter.contentStore(field);

            // External content store, with content id stored in the document
            ContentStoreExternal contentStore = (ContentStoreExternal) store;
            int contentId = contentStore.store(document);
            currentDoc.addStoredNumericField(contentIdFieldName, contentId, false);
        }
    }

    @Override
    public void index(String documentName, InputStream input) {
        index(documentName, input, null);
    }

    @Override
    public void index(String fileName, InputStream input, String fileNameGlob) {
        try (FileProcessor proc = new FileProcessor(numberOfThreadsToUse, defaultRecurseSubdirs,
                this.processArchivesAsDirectories)) {
            proc.setFileNameGlob(fileNameGlob);
            proc.setFileHandler(docIndexerWrapper);
            proc.setErrorHandler(listener());
            proc.processFile(FileReference.fromInputStream(fileName, input, null));
        }
    }

    @Override
    public void index(File file) {
        index(file, "*");
    }

    @Override
    public void index(File file, String fileNameGlob) {
        Optional<String> optGlob = Optional.ofNullable(fileNameGlob);
        try (FileProcessor proc = new FileProcessor(numberOfThreadsToUse, defaultRecurseSubdirs, processArchivesAsDirectories)) {
            proc.setFileNameGlob(optGlob.orElse("*"));
            proc.setFileHandler(docIndexerWrapper);
            proc.setErrorHandler(listener());
            proc.processFileOrDirectory(file);
        }
    }
    
    @Override
    public void index(String fileName, byte[] contents, String fileNameGlob) {
        Optional<String> optGlob = Optional.ofNullable(fileNameGlob);
        try (FileProcessor proc = new FileProcessor(numberOfThreadsToUse, defaultRecurseSubdirs, processArchivesAsDirectories)) {
            proc.setFileNameGlob(optGlob.orElse("*"));
            proc.setFileHandler(docIndexerWrapper);
            proc.setErrorHandler(listener());
            proc.processFile(FileReference.fromBytes(fileName, contents, (File)null));
        }
    }
    
    /**
     * Should we continue indexing or stop?
     *
     * We stop if we've reached the maximum that was set (if any), or if a fatal
     * error has occurred (indicated by terminateIndexing).
     *
     * @return true if we should continue, false if not
     */
    @Override
    public synchronized boolean continueIndexing() {
        if (!indexWriter.isOpen())
            return false;
        if (maxNumberOfDocsToIndex >= 0) {
            return docsToDoLeft() > 0;
        }
        return true;
    }

    /**
     * How many more documents should we process?
     *
     * @return the number of documents
     */
    @Override
    public synchronized int docsToDoLeft() {
        if (maxNumberOfDocsToIndex < 0)
            return maxNumberOfDocsToIndex;
        int docsDone = indexWriter.writer().getNumberOfDocs();
        return Math.max(0, maxNumberOfDocsToIndex - docsDone);
    }

    @Override
    public void setIndexerParam(Map<String, String> indexerParam) {
        this.indexerParam = indexerParam;
    }

    /**
     * Get the parameters we would like to be passed to the DocIndexer class.
     *
     * Used by DocIndexer classes to get their parameters.
     *
     * @return the parameters
     */
    @Override
    public Map<String, String> indexerParameters() {
        return indexerParam;
    }

    @Override
    public BlackLabIndexWriter indexWriter() {
        return indexWriter;
    }

    @Override
    public void setLinkedFileDirs(List<File> linkedFileDirs) {
        this.linkedFileDirs.clear();
        this.linkedFileDirs.addAll(linkedFileDirs);
    }

    @Override
    public void setLinkedFileResolver(Function<String, File> resolver) {
        this.linkedFileResolver = resolver;
    }

    @Override
    public Optional<Function<String, File>> linkedFileResolver() {
        return Optional.of(this.linkedFileResolver);
    }

    @Override
    public File linkedFile(String inputFile) {
        File f = new File(inputFile);
        if (f.exists())
            return f; // either absolute or relative to current dir
        if (f.isAbsolute())
            return null; // we tried absolute, but didn't find it

        // Look in the configured directories for the relative path
        f = FileUtil.findFile(linkedFileDirs, inputFile, null);
        if (f == null && this.linkedFileResolver != null)
            f = this.linkedFileResolver.apply(inputFile);

        return f;
    }

    @Override
    public void setNumberOfThreadsToUse(int numberOfThreadsToUse) {
        this.numberOfThreadsToUse = numberOfThreadsToUse;

        // Some of the class-based docIndexers don't support theaded indexing
        InputFormat inputFormat = DocumentFormats.getFormat(formatIdentifier).orElseThrow();
        if (!inputFormat.isConfigurationBased()) {
            logger.info("Threaded indexing is disabled for " + formatIdentifier + " because it is not " +
                    "configuration-based (older DocIndexers may not be threadsafe, so this is a precaution)" );
            this.numberOfThreadsToUse = 1;
        }
    }

    @Override
    public IndexMetadataWriter metadata() {
        return indexWriter.metadata();
    }

    @Override
    public BLIndexObjectFactory indexObjectFactory() {
        return indexWriter.indexObjectFactory();
    }

    @Override
    public boolean needsPrimaryValuePayloads() {
        return indexWriter.needsPrimaryValuePayloads();
    }

    /** Get the strategy to use for indexing relations. */
    @Override
    public RelationsStrategy getRelationsStrategy() {
        return indexWriter.getRelationsStrategy();
    }

    @Override
    public void debugForceMerge() {
        try {
            ((BLIndexWriterProxyLucene) indexWriter().writer()).getWriter().forceMerge(1);
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @Override
    public WarnOnce warnOnce() {
        return warnOnce;
    }
}
