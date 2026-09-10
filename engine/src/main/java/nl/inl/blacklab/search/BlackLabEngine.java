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
import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.index.BLIndexObjectFactory;
import nl.inl.blacklab.index.BLIndexObjectFactoryLucene;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.InputFormatInfo;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex.IndexType;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.util.CurrentThreadExecutorService;
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
    private static final Set<BlackLabIndex> openIndexes = new HashSet<>();

    /** When autodetecting maxThreadsPerSearch, divide #CPUs by this number */
    private static final int THREADS_PER_SEARCH_AUTO_DIVIDER = 2;

    /** Minimum for maxThreadsPerSearch when autodetecting. */
    private static final int THREADS_PER_SEARCH_AUTO_MIN = 2;

    /** Maximum for maxThreadsPerSearch when autodetecting. */
    private static final int THREADS_PER_SEARCH_AUTO_MAX = 6;

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

    /** Thread on which we run initializations (opening forward indexes, etc.).
     *  Single-threaded because these kinds of initializations are memory and CPU heavy. */
    private final ExecutorService initializationExecutorService;

    /** Threads on which we run searches. This pool is not limited in size,
     *  but new top-level searches (i.e. not started by other searches) are queued
     *  until server load is deemed low enough that they can start.
     */
    private final ExecutorService searchExecutorService;

    /** How many threads may a single search use? */
    private int maxThreadsPerSearch;

    /** Give each searchthread a unique number */
    private final AtomicInteger threadCounter = new AtomicInteger(1);

    /** How to create indexing objects. By default, use the "direct to Lucene" implementation. */
    private BLIndexObjectFactory indexObjectFactory = BLIndexObjectFactoryLucene.INSTANCE;

    /** Was close() called on this engine? */
    private boolean wasClosed;

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

        this.maxThreadsPerSearch = maxThreadsPerSearch < 0 ? chooseDefaultMaxThreadsPerSearch() :
                maxThreadsPerSearch;
    }

    public static int chooseDefaultMaxThreadsPerSearch() {
        int n = Runtime.getRuntime().availableProcessors() / THREADS_PER_SEARCH_AUTO_DIVIDER;
        n = Math.max(Math.min(n, THREADS_PER_SEARCH_AUTO_MAX), THREADS_PER_SEARCH_AUTO_MIN);
        return n;
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
                    logger.error("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
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
    public BlackLabIndexWriter openForWriting(File directory, boolean create, String formatIdentifier) throws ErrorOpeningIndex {
        BlackLabIndexWriter indexWriter;
        if (create) {
            // Create index from format configuration (modern)
            // (or a legacy DocIndexer, but no index template file, so the defaults will be used)
            // Maybe the formatIdentifier is backed by a ConfigInputFormat (instead of
            // some other DocIndexer implementation)
            // this ConfigInputFormat could then still be used as a minimal template to setup the index
            // (if there's no ConfigInputFormat, that's okay too, a default index template will be used instead)
            InputFormatInfo inputFormat = DocumentFormats.getFormat(formatIdentifier).orElse(null);
            ConfigInputFormat config = inputFormat == null ? null : inputFormat.getConfig();

            // template might still be null, in that case a default will be created
            indexWriter = openForWriting(directory, true, config);
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
        synchronized (openIndexes) {
            List<BlackLabIndex> copy = new ArrayList<>(openIndexes); // avoid concurrent mod.
            for (BlackLabIndex index: copy) {
                index.close();
            }
        }
        synchronized (engines) {
            engines.remove(this);
        }
    }

    public BlackLabIndex open(File indexDir) throws ErrorOpeningIndex {
        // Detect index type and instantiate appropriate class
        checkSupportedIndexType(indexDir, false);
        return new BlackLabIndexImpl(indexDir.getName(), this, null, indexDir, false, false, null);
    }

    private static void checkSupportedIndexType(File indexDir, boolean createNewIndex) {
        IndexType indexType;
        if (createNewIndex || !BlackLabIndex.isIndex(indexDir)) {
            // New index. Use the default type
            indexType = null;
        } else {
            // Existing index. Detect type.
            indexType = VersionFile.exists(indexDir) ? IndexType.EXTERNAL_FILES : IndexType.INTEGRATED;
        }
        if (indexType == IndexType.EXTERNAL_FILES)
            throw new IndexVersionMismatch("This index (" + indexDir + ") uses an older file format (with and external forward index) that is no longer supported by BlackLab. Use BlackLab 4.x to open it.");
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
        return new BlackLabIndexImpl(indexName, this, reader, null, indexMode, false,
                null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param forceCreateNew if true, create a new index even if one existed there
     * @return index writer
     * @throws ErrorOpeningIndex if index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean forceCreateNew)
            throws ErrorOpeningIndex {
        // If no preference for index type given, use the current default
        checkSupportedIndexType(indexDir, forceCreateNew);
        return new BlackLabIndexImpl(indexDir.getName(), this, null, indexDir, true, forceCreateNew, null);
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

        if (config != null && config.getCorpusConfig().getSpecialFields().get(MetadataFields.SPECIAL_FIELD_SETTING_PID) == null) {
            logger.warn("YOUR DOCUMENT IDs ARE NOT PERSISTENT! The input format " + config.getName() + " " +
                    "does not specify a persistent identifier (pid) field. This will work, but random ids will " +
                    "be assigned to your documents every time you index. So reindexing may assign totally different " +
                    "document ids, and any saved links to documents will break. " +
                    "To fix this, specify a pidField using the corpusConfig.specialFields.pidField setting of your " +
                    "input format configuration (.blf.yaml file).");
        }

        checkSupportedIndexType(indexDir, createNewIndex);
        return new BlackLabIndexImpl(indexDir.getName(), this, null, indexDir, true, createNewIndex, config);
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
        openIndexes.add(index);
    }

    public synchronized void removeIndex(BlackLabIndex index) {
        openIndexes.remove(index);
        if (BlackLab.isImplicitInstance(this) && openIndexes.isEmpty()) {
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
     * Get the appropriate search executor service for the given number of threads.
     *
     * If the number of threads is 2 or more, we use the regular
     * search executor service. If the number of threads is 1, we just use a
     * CurrentThreadExecutorService.
     *
     * @param numThreads number of threads to use for searching
     * @return the executor service
     */
    public ExecutorService searchExecutorService(int numThreads) {
        return numThreads >= 2
                ? searchExecutorService()
                : new CurrentThreadExecutorService();
    }

    synchronized  BlackLabIndexWriter openForWriting(String indexName, IndexReader reader) throws ErrorOpeningIndex {
        return (BlackLabIndexWriter) wrapIndexReader(indexName, reader, true);
    }

    public int maxThreadsPerSearch() {
        return maxThreadsPerSearch;
    }

    public void setMaxThreadsPerSearch(int max) {
        if (max < 1)
            throw new IllegalArgumentException("maxThreadsPerSearch must be at least 1 (got " + max + ")");
        this.maxThreadsPerSearch = max;
    }

    public BLIndexObjectFactory indexObjectFactory() {
        return indexObjectFactory;
    }
}
