package nl.inl.blacklab.search;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.index.BLIndexObjectFactory;
import nl.inl.blacklab.index.BLIndexObjectFactoryLucene;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex.IndexType;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.util.VersionFile;

/**
 * Main BlackLab instance, from which indexes can be opened.
 *
 * If you don't instantiate this, but call BlackLab.openIndex() directly,
 * an implicit instance will be created that will be closed when you close
 * the last index.
 *
 * Instantiating this explicitly has the advantage of being able to pass
 * parameters, such as the number of search thread you want (default 4).
 */
public final class BlackLabEngine implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(BlackLabEngine.class);

    /** Time to wait for tasks in the pool to finish before terminating them */
    public static final int POOL_GRACEFUL_WAIT_SEC = 10;

    /** Time to wait for tasks in the pool being terminated */
    public static final int POOL_TERMINATE_WAIT_SEC = 10;

    /**
     * All BlackLabEngines that have been instantiated, so we can close them on shutdown.
     */
    private static final Set<BlackLabEngine> engines = new HashSet<>();

    /**
     * Map from IndexReader to BlackLab, for use from inside SpanQuery/Spans classes
     */
    private static final Map<IndexReader, BlackLabEngine> indexReader2BlackLabEngine = new IdentityHashMap<>();

    /** Close all opened engines */
    static synchronized void closeAll() {
        List<BlackLabEngine> copy = new ArrayList<>(engines);
        engines.clear();
        for (BlackLabEngine engine : copy) {
            engine.close();
        }
    }

    static {
        // On program exit, make sure all the engines (and their threads) have been closed, or we might hang.
        Runtime.getRuntime().addShutdownHook(new Thread(BlackLabEngine::closeAll));
    }

    /**
     * Map from IndexReader to BlackLabIndex, for use from inside SpanQuery/Spans classes
     */
    private final Map<IndexReader, BlackLabIndex> indexReader2BlackLabIndex = new IdentityHashMap<>();

    /** Thread on which we run initializations (opening forward indexes, etc.).
     *  Single-threaded because these kinds of initializations are memory and CPU heavy. */
    private final ExecutorService initializationExecutorService;

    /** Threads on which we run searches. This pool is not limited in size,
     *  but new top-level searches (i.e. not started by other searches) are queued
     *  until server load is deemed low enough that they can start.
     */
    private final ExecutorService searchExecutorService;

    /** How many threads may a single search use? */
    private final int maxThreadsPerSearch;

    /** Give each searchthread a unique number */
    private final AtomicInteger threadCounter = new AtomicInteger(1);

    /** How to create indexing objects. By default, use the "direct to Lucene" implementation. */
    private BLIndexObjectFactory indexObjectFactory = BLIndexObjectFactoryLucene.INSTANCE;

    /** Was close() called on this engine? */
    private boolean wasClosed;

    /**
     * Create a new engine instance.
     *
     * @param searchThreads (ignored)
     * @param maxThreadsPerSearch max. threads per search.
     * @deprecated use {@link #BlackLabEngine(int)}
     */
    @SuppressWarnings("unused")
    @Deprecated
    BlackLabEngine(int searchThreads, int maxThreadsPerSearch) {
        this(maxThreadsPerSearch);
    }

    BlackLabEngine(int maxThreadsPerSearch) {
        synchronized (engines) {
            engines.add(this);
        }
        initializationExecutorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread worker = Executors.defaultThreadFactory().newThread(runnable);
            int threadNumber = threadCounter.getAndUpdate(i -> (i + 1) % 10000);
            worker.setDaemon(true); // don't prevent JVM exiting
            worker.setName("BLInit-" + threadNumber);
            return worker;
        });
        this.searchExecutorService = Executors.newCachedThreadPool(runnable -> {
            Thread worker = Executors.defaultThreadFactory().newThread(runnable);
            int threadNumber = threadCounter.getAndUpdate(i -> (i + 1) % 10000);
            worker.setName("BLSearch-" + threadNumber);
            return worker;
        });

        this.maxThreadsPerSearch = maxThreadsPerSearch;
    }

    /**
     * Set the index object factory to use.
     *
     * Use this to indicate whether we're indexing directly to Lucene (default)
     * or via Solr.
     *
     * CAUTION: call this once, before doing anything else with this engine, or
     * unpredictable behaviour may result!
     *
     * @param factory index object factory to use
     */
    public void setIndexObjectFactory(BLIndexObjectFactory factory) {
        this.indexObjectFactory = factory;
    }

    /**
     * Gracefully shut down an ExecutorService.
     *
     * Taken from <a href="https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html">Java docs</a>.
     *
     * @param pool thread pool to shut down
     */
    private static void closeExecutorPool(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(POOL_GRACEFUL_WAIT_SEC, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(POOL_TERMINATE_WAIT_SEC, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public static BlackLabIndex indexFromReader(String indexName, IndexReader reader, boolean wrapIfNotFound, boolean writeMode) {
        BlackLabEngine blackLabEngine;
        synchronized (indexReader2BlackLabEngine) {
            blackLabEngine = indexReader2BlackLabEngine.get(reader);
        }
        if (blackLabEngine == null && wrapIfNotFound) {
            // If the IndexReader doesn't have a BlackLabIndex yet, create one in the implicit engine.
            // (used with Solr, who manages IndexReaders for us)
            blackLabEngine = BlackLab.implicitInstance();
        }
        return blackLabEngine == null ? null : blackLabEngine.getIndexFromReader(indexName, reader, wrapIfNotFound, writeMode);
    }

    /**
     * Create or open an index.
     *
     * @param directory index directory
     * @param create force creating a new index even if one already exists?
     * @param formatIdentifier default document format to use
     * @return the index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File directory, boolean create, String formatIdentifier)
            throws ErrorOpeningIndex {
        return openForWriting(directory, create, formatIdentifier, null, null);
    }

    /**
     * Create or open an index.
     *
     * @param directory index directory
     * @param create force creating a new index even if one already exists?
     * @param formatIdentifier default document format to use
     * @param indexTemplateFile optional file to use as template for index (legacy)
     * @return the index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File directory, boolean create, String formatIdentifier,
            File indexTemplateFile) throws ErrorOpeningIndex {
        return openForWriting(directory, create, formatIdentifier, indexTemplateFile, null);
    }

    /**
     * Create or open an index.
     *
     * @param directory index directory
     * @param create force creating a new index even if one already exists?
     * @param formatIdentifier default document format to use
     * @param indexTemplateFile optional file to use as template for index (legacy)
     * @param indexType index format to use for creating index: classic with external files or integrated
     * @return the index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File directory, boolean create, String formatIdentifier,
            File indexTemplateFile, IndexType indexType) throws ErrorOpeningIndex {
        BlackLabIndexWriter indexWriter;
        if (create) {
            if (indexTemplateFile == null) {
                // Create index from format configuration (modern)
                // (or a legacy DocIndexer, but no index template file, so the defaults will be used)
                // No indexTemplateFile, but maybe the formatIdentifier is backed by a ConfigInputFormat (instead of
                // some other DocIndexer implementation)
                // this ConfigInputFormat could then still be used as a minimal template to setup the index
                // (if there's no ConfigInputFormat, that's okay too, a default index template will be used instead)
                InputFormat inputFormat = DocumentFormats.getFormat(formatIdentifier).orElse(null);
                ConfigInputFormat config = inputFormat == null ? null : inputFormat.getConfig();

                // template might still be null, in that case a default will be created
                indexWriter = openForWriting(directory, true, config, indexType);
            } else {
                // Create index from index template file (legacy)
                indexWriter = openForWriting(directory, true, indexTemplateFile, indexType);
            }
            BlackLabIndexWriter.setMetadataDocumentFormatIfMissing(indexWriter, formatIdentifier);
        } else {
            // opening an existing index
            indexWriter = openForWriting(directory, false);
        }
        return indexWriter;
    }

    @Override
    public synchronized void close() {
        if (wasClosed)
            return;
        wasClosed = true;
        closeExecutorPool(searchExecutorService);
        closeExecutorPool(initializationExecutorService);
        synchronized (indexReader2BlackLabIndex) {

            List<BlackLabIndex> copy = new ArrayList<>(indexReader2BlackLabIndex.values()); // avoid concurrent mod.
            for (BlackLabIndex index: copy) {
                index.close();
            }
        }
        synchronized (engines) {
            engines.remove(this);
        }
    }

    public BlackLabIndexWriter openForWriting(String indexName, IndexReader reader, ConfigInputFormat format,
            IndexType indexType) throws ErrorOpeningIndex {
        if (indexType != IndexType.INTEGRATED)
            throw new RuntimeException("This version of the method only works with integrated indexes");
        return new BlackLabIndexIntegrated(indexName, this, reader, null, true, false, format);
    }

    /**
     * Return the current default index type, external or integrated
     * @return the default index type
     */
    public IndexType getDefaultIndexType() {
        String defaultIndexType = BlackLab.featureFlag(BlackLab.FEATURE_DEFAULT_INDEX_TYPE);
        return defaultIndexType.equalsIgnoreCase("external") ?
                IndexType.EXTERNAL_FILES :
                IndexType.INTEGRATED;
    }

    public BlackLabIndex open(File indexDir) throws ErrorOpeningIndex {
        // Detect index type and instantiate appropriate class
        IndexType indexType = determineIndexType(indexDir, false, null);
        return indexType == IndexType.INTEGRATED ?
            new BlackLabIndexIntegrated(indexDir.getName(), this, null, indexDir, false, false, null):
            new BlackLabIndexExternal(this, indexDir, false, false, (File) null);
    }

    /**
     * Get a BlackLabIndex instance from an already opened IndexReader.
     *
     * Used for Solr integration, where Solr manages IndexReader instances.
     *
     * CAUTION: this only works with the integrated index format. 
     *
     * @param reader reader to wrap
     * @return a BlackLabIndex instance with this reader
     */
    public BlackLabIndex wrapIndexReader(String indexName, IndexReader reader, boolean indexMode) throws ErrorOpeningIndex {
        return new BlackLabIndexIntegrated(indexName, this, reader, null, indexMode, false,
                null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param forceCreateNew if true, create a new index even if one existed there
     * @return index writer
     * @throws ErrorOpeningIndex if the index could not be opened
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean forceCreateNew) throws ErrorOpeningIndex {
        return openForWriting(indexDir, forceCreateNew, (File) null, null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param forceCreateNew if true, create a new index even if one existed there
     * @param indexTemplateFile JSON template to use for index structure / metadata
     * @return index writer
     * @throws ErrorOpeningIndex if index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean forceCreateNew, File indexTemplateFile)
            throws ErrorOpeningIndex {
        return openForWriting(indexDir, forceCreateNew, indexTemplateFile, null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param forceCreateNew if true, create a new index even if one existed there
     * @param indexTemplateFile (optional) JSON template to use for index structure / metadata
     *                          (only works with {@link IndexType#EXTERNAL_FILES}; pass null for integrated)
     * @param indexType (optional) type of index to create (external or integrated), or null to use the default type
     * @return index writer
     * @throws ErrorOpeningIndex if index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean forceCreateNew, File indexTemplateFile, IndexType indexType)
            throws ErrorOpeningIndex {
        // If no preference for index type given, use the current default
        indexType = determineIndexType(indexDir, forceCreateNew, indexType);
        if (indexType == IndexType.EXTERNAL_FILES)
            return new BlackLabIndexExternal(this, indexDir, true, forceCreateNew, indexTemplateFile);

        if (indexTemplateFile != null)
            throw new IllegalArgumentException("Cannot use index template file with integrated index!");
        return new BlackLabIndexIntegrated(indexDir.getName(), this, null, indexDir, true, forceCreateNew, null);
    }

    private IndexType determineIndexType(File indexDir, boolean forceCreateNew, IndexType indexType) {
        if (indexType == null) {
            // Index type not specified.
            if (forceCreateNew || !BlackLabIndex.isIndex(indexDir)) {
                // New index. Use the default type
                indexType = getDefaultIndexType();
            } else {
                // Existing index. Detect type.
                indexType = VersionFile.exists(indexDir) ? IndexType.EXTERNAL_FILES : IndexType.INTEGRATED;
            }
        }
        return indexType;
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     * @return index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, ConfigInputFormat config)
            throws ErrorOpeningIndex {
        return openForWriting(indexDir, createNewIndex, config, null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     * @param indexType type of index to create (external or integrated), or null to use the default type
     * @return index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, ConfigInputFormat config, IndexType indexType)
            throws ErrorOpeningIndex {

        if (config != null && config.getCorpusConfig().getSpecialFields().get(MetadataFields.SPECIAL_FIELD_SETTING_PID) == null) {
            logger.warn("YOUR DOCUMENT IDs ARE NOT PERSISTENT! The input format " + config.getName() + " " +
                    "does not specify a persistent identifier (pid) field. This will work, but random ids will " +
                    "be assigned to your documents every time you index. So reindexing may assign totally different " +
                    "document ids, and any saved links to documents will break. " +
                    "To fix this, specify a pidField using the corpusConfig.specialFields.pidField setting of your " +
                    "input format configuration (.blf.yaml file).");
        }

        indexType = determineIndexType(indexDir, createNewIndex, indexType);
        return indexType == IndexType.INTEGRATED ?
                new BlackLabIndexIntegrated(indexDir.getName(), this, null, indexDir, true, createNewIndex, config) :
                new BlackLabIndexExternal(this, indexDir, true, createNewIndex, config);
    }

    /**
     * Create an empty index.
     *
     * @param indexDir where to create the index
     * @param config format configuration for this index; used to base the index
     *            metadata on
     * @return a BlackLabIndexWriter for the new index, in index mode
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public BlackLabIndexWriter create(File indexDir, ConfigInputFormat config) throws ErrorOpeningIndex {
        return openForWriting(indexDir, true, config);
    }

    public synchronized void registerIndex(IndexReader reader, BlackLabIndex index) {
        indexReader2BlackLabIndex.put(reader, index);
        synchronized (indexReader2BlackLabEngine) {
            indexReader2BlackLabEngine.put(reader, this);
        }
    }

    public synchronized void removeIndex(BlackLabIndex index) {
        synchronized (indexReader2BlackLabEngine) {
            indexReader2BlackLabEngine.remove(index.reader());
        }
        indexReader2BlackLabIndex.remove(index.reader());
        if (BlackLab.isImplicitInstance(this) && indexReader2BlackLabIndex.isEmpty()) {
            // We are the implicit instance and our last searcher has been closed. Clean up.
            try {
                close();
            } finally {
                BlackLab.discardImplicitInstance();
            }
        }
    }

    public ExecutorService initializationExecutorService() {
        return initializationExecutorService;
    }

    public ExecutorService searchExecutorService() {
        return searchExecutorService;
    }

    /**
     * Given an IndexReader, return corresponding BlackLabIndex.
     *
     * @param reader IndexReader to get the BlackLabIndex for
     * @param wrapIfNotFound if true, a new BlackLabIndex instance will be created for this IndexReader if none
     *                       existed yet. Used with Solr.
     * @return BlackLabIndex instance for this IndexReader
     */
    public synchronized BlackLabIndex getIndexFromReader(String indexName, IndexReader reader, boolean wrapIfNotFound, boolean writeMode) {
        BlackLabIndex blackLabIndex = indexReader2BlackLabIndex.get(reader);
        if (blackLabIndex == null && wrapIfNotFound) {
            // We don't have a BlackLabIndex instance for this IndexReader yet. This can occur if e.g.
            // Solr is in charge of opening IndexReaders. Create a new instance now and register it.
            try {
                blackLabIndex = wrapIndexReader(indexName, reader, false);
                registerIndex(reader, blackLabIndex);
            } catch (ErrorOpeningIndex e) {
                throw new RuntimeException(e);
            }
        }
        return blackLabIndex;
    }

    synchronized  BlackLabIndexWriter openForWriting(String indexName, IndexReader reader) throws ErrorOpeningIndex {
        return (BlackLabIndexWriter) wrapIndexReader(indexName, reader, true);
    }

    public int maxThreadsPerSearch() {
        return maxThreadsPerSearch;
    }

    public BLIndexObjectFactory indexObjectFactory() {
        return indexObjectFactory;
    }
}
