package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.Collator;
import java.text.RuleBasedCollator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.config.BLConfigIndexing;
import nl.inl.blacklab.config.BlackLabConfig;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.util.DownloadCache;
import nl.inl.blacklab.index.PluginManager;
import nl.inl.blacklab.index.ZipHandleManager;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex.IndexType;
import nl.inl.util.FileUtil;

/**
 * Main BlackLab class, from which indexes can be opened.
 * 
 * You can either open indices using the static methods in this class,
 * or you can create() a BlackLabEngine and use that to open indices.
 * 
 * The first approach will implicitly create a default BlackLabEngine
 * in the background, with 4 search threads. If you want a different
 * number of search threads, call create() to create your own instance
 * of BlackLabEngine.
 * 
 * Don't try to mix these two methods; if an implicit engine exists and
 * you call create(), or if you call e.g. BlackLab.open() when you've
 * already created an engine explicitly, an exception will be thrown.
 * 
 * If you explicitly create an engine, make sure to close it when you're
 * done. For the implicit engine, this is done automatically when you
 * close your last index. 
 */
public final class BlackLab {
    private static final Logger logger = LogManager.getLogger(BlackLab.class);

    /** If no explicit BlackLab instance is created, how many threads per search should we use? */
    private static final int DEFAULT_MAX_THREADS_PER_SEARCH = 2;

    /**
     * If client doesn't explicitly create a BlackLab instance, one will be instantiated
     * automatically.
     */
    private static BlackLabEngine implicitInstance = null;
    
    /**
     * Have we called create()? If so, don't create an implicit instance, but throw an exception.
     */
    private static boolean explicitlyCreated = false;
    
    /** Cache for getConfigDirs() */
    private static List<File> configDirs;

    /** BlackLab configuration */
    private static BlackLabConfig blackLabConfig = null;

    /** Global settings are read from file and applied to the different parts of BL once. */
    private static boolean globalSettingsApplied = false;

    /** Controls what BlackLab's default index type is. If not present, will default to the new
     *  integrated index. Set to 'external' to use the legacy index with external forward index
     *  that was the default in BlackLab 3.x. Used for testing.
     */
    public static final String FEATURE_DEFAULT_INDEX_TYPE = "defaultIndexType";

    /** The default XML processing library to use. Valid values are "vtd" and "saxon".
     *  VTD-XML, the current default, is memory-efficient but generally slower and doesn't support recent XPath.
     *  Saxon uses more memory but is faster and has better XPath support.
     *  The default will change to Saxon in the near future.
     */
    public static final String FEATURE_DEFAULT_XML_PROCESSOR = "defaultXmlProcessor";

    /** Force a merge after every document? Can be useful when debugging Lucene codec issues. */
    public static final String FEATURE_DEBUG_FORCE_MERGE = "debugForceMerge";

    private static Collator fieldValueSortCollator = null;

    /**
     * Create a new engine instance.
     *
     * @param searchThreads (ignored)
     * @param maxThreadsPerSearch max. threads per search.
     * @deprecated use {@link #createEngine(int)}
     */
    @SuppressWarnings("unused")
    @Deprecated
    public static BlackLabEngine createEngine(int searchThreads, int maxThreadsPerSearch) {
        return createEngine(maxThreadsPerSearch);
    }

    /**
     * Create a new engine instance.
     *
     * @param maxThreadsPerSearch max. threads per search.
     */
    public static BlackLabEngine createEngine(int maxThreadsPerSearch) {
        if (implicitInstance != null)
            throw new UnsupportedOperationException("BlackLab.create() called, but an implicit instance exists already! Don't mix implicit and explicit BlackLabEngine!");
        explicitlyCreated = true;
        return new BlackLabEngine(maxThreadsPerSearch);
    }

    public static BlackLabIndex open(File dir) throws ErrorOpeningIndex {
        return implicitInstance().open(dir);
    }
    
    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @return index writer
     * @throws ErrorOpeningIndex if the index could not be opened
     */
    public static BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex) throws ErrorOpeningIndex {
        return openForWriting(indexDir, createNewIndex, (File) null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @param indexTemplateFile JSON template to use for index structure / metadata
     * @return index writer
     * @throws ErrorOpeningIndex if index couldn't be opened
     */
    public static BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, File indexTemplateFile)
            throws ErrorOpeningIndex {
        return implicitInstance().openForWriting(indexDir, createNewIndex, indexTemplateFile);
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
    public static BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, ConfigInputFormat config)
            throws ErrorOpeningIndex {
        return implicitInstance().openForWriting(indexDir, createNewIndex, config);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param directory the index directory
     * @param create if true, create a new index even if one existed there
     * @param formatIdentifier default format to use
     * @return index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public static BlackLabIndexWriter openForWriting(File directory, boolean create, String formatIdentifier) throws ErrorOpeningIndex {
        return openForWriting(directory, create, formatIdentifier, null, null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param directory the index directory
     * @param create if true, create a new index even if one existed there
     * @param formatIdentifier default format to use
     * @param indexTemplateFile (optional, legacy) index template file
     * @return index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public static BlackLabIndexWriter openForWriting(File directory, boolean create, String formatIdentifier,
            File indexTemplateFile) throws ErrorOpeningIndex {
        return openForWriting(directory, create, formatIdentifier, indexTemplateFile, null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param directory the index directory
     * @param create if true, create a new index even if one existed there
     * @param formatIdentifier default format to use
     * @param indexTemplateFile (optional, legacy) index template file
     * @param indexType index format to use: classic with external files or new integrated
     * @return index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public static BlackLabIndexWriter openForWriting(File directory, boolean create, String formatIdentifier,
            File indexTemplateFile, IndexType indexType) throws ErrorOpeningIndex {
        return implicitInstance().openForWriting(directory, create, formatIdentifier, indexTemplateFile, indexType);
    }

    public static BlackLabIndexWriter openForWriting(String indexName, IndexReader reader) throws ErrorOpeningIndex {
        return (BlackLabIndexWriter) implicitInstance.wrapIndexReader(indexName, reader, true);
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
    @Deprecated
    public static BlackLabIndexWriter create(File indexDir, ConfigInputFormat config) throws ErrorOpeningIndex {
        return openForWriting(indexDir, true, config);
    }
    
    
    /**
     * Return the implicitly created instance of BlackLab.
     * 
     * Only used by BlackLabIndex if no BlackLab instance is provided during opening.
     * The instance will have 4 search threads.
     * 
     * @return implicitly instantiated BlackLab instance
     */
    public static synchronized BlackLabEngine implicitInstance() {
        if (explicitlyCreated)
            throw new UnsupportedOperationException("Already called create(); cannot create an implicit instance anymore! Don't mix implicit and explicit BlackLabEngine!");
        if (implicitInstance == null) {
            implicitInstance = new BlackLabEngine(DEFAULT_MAX_THREADS_PER_SEARCH);
        }
        return implicitInstance;
    }

    public static synchronized void discardImplicitInstance() {
        implicitInstance = null;
    }

    public static boolean isImplicitInstance(BlackLabEngine blackLabEngine) {
        return blackLabEngine == implicitInstance;
    }

    /**
     * Given an index reader that was opened using BlackLab, return the
     * corresponding BlackLab index object.
     *
     * @param reader index reader that was opened using BlackLab
     * @param wrapIfNotFound if not found, should we create a new instance using the supplied reader?
     * @return BlackLab index object
     */
    public static synchronized BlackLabIndex indexFromReader(String indexName, IndexReader reader, boolean wrapIfNotFound) {
        return BlackLabEngine.indexFromReader(indexName, reader, wrapIfNotFound, false);
    }

    /**
     * Return a timestamp for when BlackLab was built.
     *
     * @return build timestamp (format: yyyy-MM-dd HH:mm:ss), or UNKNOWN if the
     *         timestamp could not be found for some reason (i.e. not running from a
     *         JAR, or key not found in manifest).
     */
    public static String buildTime() {
        return valueFromManifest("Build-Time");
    }

    /**
     * Return the BlackLab version.
     *
     * @return BlackLab version, or UNKNOWN if the version could not be found for
     *         some reason (i.e. not running from a JAR, or key not found in
     *         manifest).
     */
    public static String version() {
        return valueFromManifest("Implementation-Version");
    }

    /**
     * Return the SCM revision this was built from.
     *
     * This is generally the short Git commit hash.
     *
     * @return SCM revision string, or UNKNOWN if it could not be found
     */
    public static String getBuildScmRevision() {
        return valueFromManifest("Build-Scm-Revision");
    }

    public static Collator defaultCollator() {
        return config().getSearch().getCollator().get();
    }

    /**
     * Get a value from the manifest file, if available.
     *
     * @param key key to get the value for, e.g. "Build-Time".
     * @return value from the manifest, or the default value if not found
     */
    private static String valueFromManifest(String key) {
        try {
            URL res = BlackLabIndexAbstract.class.getResource(BlackLabIndexAbstract.class.getSimpleName() + ".class");
            String value = null;
            if (res != null) {
                URLConnection conn = res.openConnection();
                if (conn instanceof JarURLConnection) {
                    JarURLConnection jarConn = (JarURLConnection) res.openConnection();
                    Manifest mf = jarConn.getManifest();
                    if (mf != null) {
                        Attributes atts = mf.getMainAttributes();
                        if (atts != null) {
                            value = atts.getValue(key);
                        }
                    }
                }
            }
            return value == null ? "UNKNOWN" : value;
        } catch (IOException e) {
            throw new BlackLabRuntimeException("Error reading '" + key + "' from manifest", e);
        }
    }
    
    /**
     * Return a list of directories that should be searched for BlackLab-related
     * configuration files.
     *
     * May be used by applications to locate BlackLab-related configuration, such as
     * input format definition files or other configuration files. IndexTool and
     * BlackLab Server use this.
     *
     * The directories returned are (in decreasing priority):
     *
     * <ul>
     * <li>$BLACKLAB_CONFIG_DIR (if env. var. is defined)</li>
     * <li>$HOME/.blacklab</li>
     * <li>/etc/blacklab</li>
     * <li>/tmp (legacy, will be removed)</li>
     * </ul>>
     *
     * A convenient method to use with this is
     * {@link FileUtil#findFile(List, String, List)}.
     *
     * @return list of directories to search in decreasing order of priority
     */
    public synchronized static List<File> defaultConfigDirs() {
        if (configDirs == null) {
            configDirs = new ArrayList<>();
            String strConfigDir = System.getenv("BLACKLAB_CONFIG_DIR");
            if (strConfigDir != null && strConfigDir.length() > 0) {
                File configDir = new File(strConfigDir);
                if (configDir.exists()) {
                    if (!configDir.canRead())
                        logger.warn("BLACKLAB_CONFIG_DIR points to a unreadable directory: " + strConfigDir);
                    configDirs.add(configDir);
                } else {
                    logger.warn("BLACKLAB_CONFIG_DIR points to a non-existent directory: " + strConfigDir);
                }
            }
            configDirs.add(new File(System.getProperty("user.home"), ".blacklab"));
            configDirs.add(new File("/etc/blacklab"));
            configDirs.add(new File(System.getProperty("java.io.tmpdir")));
        }
        return new ArrayList<>(configDirs);
    }
    
    /**
     * Get the BlackLab config.
     * 
     * If no config has been set, return a default configuration.
     * 
     * @return currently set config
     */
    public synchronized static BlackLabConfig config() {
        if (blackLabConfig == null) {
            blackLabConfig = new BlackLabConfig();
        }
        return blackLabConfig;
    }

    /**
     * Get the value of a feature flag.
     *
     * Feature flags can be set in the environment (BLACKLAB_FEATURE_<flagName>) or in the
     * blacklab[-server].yaml configuration file under the 'featureFlags' key.
     *
     * Used for testing both index types.
     *
     * @param name name of the feature flag
     * @return value of the feature flag, or an empty string if not set
     */
    public static String featureFlag(String name) {
        String value = System.getenv("BLACKLAB_FEATURE_" + name);
        if (value == null)
            value = config().getFeatureFlags().get(name);
        return value == null ? "" : value;
    }

    /**
     * Read blacklab.yaml and set the configuration from that.
     * 
     * This must be called before you open the first index, or an exception will be thrown,
     * because another default config has been applied already.
     */
    public static synchronized void setConfigFromFile() {
        if (globalSettingsApplied)
            throw new UnsupportedOperationException("Cannot set default configuration - another configuration has already been applied.");
            
        File file = FileUtil.findFile(defaultConfigDirs(), "blacklab", Arrays.asList("yaml", "yml", "json"));
        if (file != null) {
            try {
                blackLabConfig = BlackLabConfig.readConfigFile(file);
            } catch (IOException e) {
                logger.warn("Could not load default blacklab configuration file " + file + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Set the BlackLab configuration to use.
     * 
     * This must be called before you open the first index, or an exception will be thrown,
     * because another default config has been applied already.
     * 
     * @param config configuration to use
     */
    public static synchronized void setConfig(BlackLabConfig config, boolean forceApply) {
        if (globalSettingsApplied && !forceApply)
            return;

        if (globalSettingsApplied) {
            throw new UnsupportedOperationException(
                    "Cannot set default configuration - another configuration has already been applied.");
        }
        
        blackLabConfig = config; 
    }

    /**
     * Configure the index according to the blacklab configuration.
     *
     * @param index index to apply the config to
     */
    public synchronized static void applyConfigToIndex(BlackLabIndex index) {
        ensureGlobalConfigApplied();
        
        // Apply search settings from the config to this BlackLabIndex
        blackLabConfig.getSearch().apply(index);
    }

    /**
     * This ensures that relevant configuration settings have been applied
     * to several components of BlackLab. We call configuring these components
     * "the global config" because these are shared between all indexes.
     *
     * This is called before any settings are applied to individual indexes,
     * which could cause problems.
     */
    private synchronized static void ensureGlobalConfigApplied() {
        if (!globalSettingsApplied) {

            BLConfigIndexing indexing = config().getIndexing();
            DownloadCache.setConfig(indexing.downloadCacheConfig());
            ZipHandleManager.setMaxOpen(indexing.getZipFilesMaxOpen());

            // Plugins settings
            PluginManager.initPlugins(config().getPlugins());

            globalSettingsApplied = true;
        }
    }

    private BlackLab() { }

    /**
     * Returns a collator that sort field values "properly", ignoring parentheses.
     *
     * @return the collator
     */
    public static Collator getFieldValueSortCollator() {
        if (fieldValueSortCollator == null) {
            fieldValueSortCollator = defaultCollator();
            if (fieldValueSortCollator instanceof RuleBasedCollator) {
                try {
                    // Make sure it ignores parentheses when comparing
                    String rules = ((RuleBasedCollator)fieldValueSortCollator).getRules();
                    // Set parentheses equal to NULL, which is ignored.
                    rules += "&\u0000='('=')'";
                    fieldValueSortCollator = new RuleBasedCollator(rules);
                } catch (Exception e) {
                    // Oh well, we'll use the collator as-is
                    //throw new RuntimeException();//DEBUG
                }
            }
        }
        return fieldValueSortCollator;
    }
}
