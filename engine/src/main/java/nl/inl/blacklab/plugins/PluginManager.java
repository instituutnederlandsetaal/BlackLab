package nl.inl.blacklab.plugins;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import groovy.lang.GroovyShell;
import nl.inl.blacklab.config.BLConfigPlugins;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.indexers.config.InputFormatTypeBase;
import nl.inl.blacklab.indexers.config.InputFormatTypeChat;
import nl.inl.blacklab.indexers.config.InputFormatTypeCoNLLU;
import nl.inl.blacklab.indexers.config.InputFormatTypePlainText;
import nl.inl.blacklab.indexers.config.InputFormatTypeTabular;
import nl.inl.blacklab.indexers.config.InputFormatTypeWithConverters;
import nl.inl.blacklab.indexers.config.InputFormatTypeXml;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionAppend;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionConcatDate;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionIdentity;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionIfEmpty;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionMapValues;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionMultiple;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionParsePos;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionReplace;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionSort;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionSplit;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionStrip;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionUnique;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.extensions.QueryFunctionAbs;
import nl.inl.blacklab.search.extensions.QueryFunctionEnd;
import nl.inl.blacklab.search.extensions.QueryFunctionFixedSpan;
import nl.inl.blacklab.search.extensions.QueryFunctionFuzzy;
import nl.inl.blacklab.search.extensions.QueryFunctionGap;
import nl.inl.blacklab.search.extensions.QueryFunctionInRange;
import nl.inl.blacklab.search.extensions.QueryFunctionLambda;
import nl.inl.blacklab.search.extensions.QueryFunctionLen;
import nl.inl.blacklab.search.extensions.QueryFunctionList;
import nl.inl.blacklab.search.extensions.QueryFunctionMeet;
import nl.inl.blacklab.search.extensions.QueryFunctionMeetWithin;
import nl.inl.blacklab.search.extensions.QueryFunctionQuery;
import nl.inl.blacklab.search.extensions.QueryFunctionStart;
import nl.inl.blacklab.search.extensions.QueryFunctionStr;
import nl.inl.blacklab.search.extensions.QueryFunctionSymbol;
import nl.inl.blacklab.search.extensions.QueryFunctionUnion;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorerDice;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorerSalience;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorerSize;

/**
 * Responsible for loading file conversion and tagging plugins.
 * <p>
 * It will attempt to load all conversion and tagging plugins (according to the
 * {@link ServiceLoader} system) and initialize them with their respective
 * settings from the main blacklab config.
 * <p>
 * A jar that wishes to register a plugin must contain a file named
 * "nl.inl.blacklab.indexers.preprocess.(Tag|Covert)Plugin" inside
 * "META-INF/services/", containing the qualified classNames of the
 * implementations they contain.
 */
public class PluginManager {

    private static final Logger logger = LogManager.getLogger(PluginManager.class);

    /** Where plugins and their configs can be found. */
    private static File pluginsDir;

    /** Types of plugins */
    private static final List<Class<? extends Plugin>> pluginTypes = new ArrayList<>(Arrays.asList(
            FileConverter.class,
            DocTaskType.class,
            HitGroupScorerType.class,
            IndexSourceType.class,
            InputFormatType.class,
            ProcessingInstruction.class,
            QueryFunction.class,
            QueryParserProvider.class));

    /** Name of the plugins directory in the main configuration directory */
    public static final String PLUGINS_DIR_NAME = "plugins";

    /**
     * Delay initialization of plugins until they are first used. Useful for
     * development
     */
    private static final String PROP_DELAY_INITIALIZATION = "delayInitialization";

    /** Is the plugin system itself initialized */
    private static boolean isInitialized = false;

    private static final Map<Class<? extends Plugin>, PluginsOfType<? extends Plugin>> pluginsByType = new HashMap<>();

    private static final Set<Class<? extends Plugin>> safePluginClasses = new HashSet<>();

    static {
        addWebSafePlugins(List.of(
                // FileConverter
                // DocTaskType
                // IndexSourceType
                // (whitelist explicitly if needed)

                // HitGroupScorerType
                HitGroupScorerDice.class,
                HitGroupScorerSalience.class,
                HitGroupScorerSize.class,

                // InputFormatType
                InputFormatTypeWithConverters.class,
                InputFormatTypeXml.class,
                InputFormatTypePlainText.class,
                InputFormatTypeBase.class,
                InputFormatTypeTabular.class,
                InputFormatTypeCoNLLU.class,
                InputFormatTypeChat.class,

                // ProcessingInstruction
                ProcessingInstructionUnique.class,
                ProcessingInstructionSort.class,
                ProcessingInstructionReplace.class,
                ProcessingInstructionConcatDate.class,
                ProcessingInstructionMultiple.class,
                ProcessingInstructionMapValues.class,
                ProcessingInstructionParsePos.class,
                ProcessingInstructionAppend.class,
                ProcessingInstructionIdentity.class,
                ProcessingInstructionSplit.class,
                ProcessingInstructionStrip.class,
                ProcessingInstructionIfEmpty.class,

                // QueryFunction
                QueryFunctionAbs.class,
                QueryFunctionEnd.class,
                QueryFunctionFixedSpan.class,
                QueryFunctionFuzzy.class,
                QueryFunctionGap.class,
                QueryFunctionInRange.class,
                QueryFunctionLambda.class,
                QueryFunctionLen.class,
                QueryFunctionList.class,
                QueryFunctionMeet.class,
                QueryFunctionMeetWithin.class,
                QueryFunctionQuery.class,
                QueryFunctionStart.class,
                QueryFunctionStr.class,
                QueryFunctionSymbol.class,
                QueryFunctionUnion.class
        ));
    }

    public static synchronized void addWebSafePlugins(List<Class<? extends Plugin>> pluginClasses) {
        safePluginClasses.addAll(pluginClasses);
    }

    public static synchronized <T extends Plugin> boolean isAllowed(Plugin plugin) {
        ensureInitialized();
        return safePluginClasses.contains(plugin.getClass());
    }

    private static synchronized void ensureInitialized() {
        if (!isInitialized)
            initialize(BlackLab.config().getPlugins(), BlackLab.configDir());
    }

    /** Groovy plugin script we've found but not loaded yet. We don't yet know its plugin type. */
    record UnloadedGroovyPlugin(File scriptFile, BLConfigPlugins pluginConfig) {}

    /** Groovy plugin scripts we've found but not loaded yet. We don't yet know their plugin type. */
    private static final Map<String, UnloadedGroovyPlugin> unloadedGroovyScripts = new LinkedHashMap<>();

    // Nothing to do; initialization happens when the blacklab config is loaded.
    // The blacklab Config is automatically loaded when the first BlackLabIndex is
    // opened, or earlier by a user library.
    // So plugin formats should always be visible by the time they're needed.
    // (except when trying to query available formats before opening an index or
    // loading a config...this is an edge case)
    private PluginManager() {
    }

    public static File getPluginsDir() {
        return pluginsDir;
    }

    public static synchronized void addPluginType(Class<? extends Plugin> pluginType) {
        if (isInitialized)
            throw new IllegalStateException("Cannot add plugin type after initialization");
        pluginTypes.add(pluginType);
    }

    /**
     * Attempts to load and initialize all plugin classes and scripts in the plugin
     * directory (and classes on the classpath), passing the values in the config
     * to the matching plugin.
     *
     * @param pluginConfig configurations per plugin id
     * @param configDir directory where plugins and their configs can be found
     */
    public static synchronized void initialize(BLConfigPlugins pluginConfig, File configDir) {
        if (isInitialized)
            throw new IllegalStateException("PluginManager already initialized");
        isInitialized = true;
        PluginManager.pluginsDir = new File(configDir, PLUGINS_DIR_NAME);

        logger.debug("Initializing plugin system...");
        
        boolean delayInitialization = pluginConfig.isDelayInitialization();

        // First load all plugins, so we have the full list of plugins available.
        try (URLClassLoader cl = getPluginsDirClassLoader(PluginManager.class.getClassLoader())) {
            for (Class<? extends Plugin> pluginClass: pluginTypes) {
                pluginsByType.put(pluginClass, new PluginsOfType<>(pluginClass, pluginConfig, cl));
            }
        } catch (IOException e) {
            logger.error("Error closing plugin classloader: " + e.getMessage(), e);
        }
        findGroovyScripts(pluginConfig);

        // Some plugins take a LONG time to init, if we block, we block the loading of the config
        // Which in turn blocks the whole of blacklab(-server), so don't do that
        if (!delayInitialization) {
            CompletableFuture.runAsync(() -> {
                // only now they're all located, initialize them
                logger.trace("Config setting " + PROP_DELAY_INITIALIZATION + " is false, initializing plugins...");
                pluginsByType.values().forEach(PluginsOfType::initializePlugins);
                logger.trace("Finished Initializing plugin system");
            });
        } else {
            logger.trace("Config setting " + PROP_DELAY_INITIALIZATION
                    + " is true, plugins will be initialized on first use.");
            logger.trace("Finished Initializing plugin system");
        }
    }

    private static URLClassLoader getPluginsDirClassLoader(ClassLoader parent) {
        List<URL> urlList = new ArrayList<>();
        FilenameFilter filenameFilter = (dir, name) -> name.toLowerCase().endsWith(".jar");
        File[] files = pluginsDir.listFiles(filenameFilter);
        if (files != null) {
            for (File f: files) {
                if (f.isFile() && f.canRead()) {
                    try {
                        urlList.add(f.toURI().toURL());
                    } catch (Exception e) {
                        logger.error("Error loading plugin jar " + f + ": " + e.getMessage(), e);
                    }
                }
            }
        }
        URL[] urls = urlList.toArray(URL[]::new);
        return new URLClassLoader(urls, parent);
    }

    private static synchronized void findGroovyScripts(BLConfigPlugins pluginConfig) {
        FilenameFilter filenameFilter = (dir, name) -> name.toLowerCase().endsWith(".groovy");
        File[] files = pluginsDir.listFiles(filenameFilter);
        if (files != null) {
            for (File scriptFile: files) {
                if (scriptFile.isFile() && scriptFile.canRead()) {
                    String scriptFileName = scriptFile.getName().replaceAll("\\.groovy$", "");
                    unloadedGroovyScripts.put(scriptFileName, new UnloadedGroovyPlugin(scriptFile, pluginConfig));
                }
            }
        }
    }

    /**
     * Make sure all groovy scripts have been loaded, so we know we have all plugins (of a certain type).
     *
     * We don't know the type of an unloaded groovy plugin, so for PluginsOfType.getAll(), all scripts need
     * to be loaded first.
     */
    static void loadAllGroovyScripts() {
        ensureInitialized();
        ArrayList<String> ids = new ArrayList<>(unloadedGroovyScripts.keySet());
        for (String id: ids) {
            getUnloaded(id);
        }
    }

    /**
     * See if there's a groovy script with this name we can load.
     *
     * @param id  plugin id (groovy script name)
     * @param <T> plugin type
     */
    static synchronized <T extends Plugin> void getUnloaded(String id) {
        ensureInitialized();
        UnloadedGroovyPlugin unloaded;
        synchronized (unloadedGroovyScripts) {
            unloaded = unloadedGroovyScripts.remove(id);
        }
        if (unloaded == null)
            return;
        GroovyShell shell = new GroovyShell();
        try {
            Object result = shell.evaluate(FileUtils.readFileToString(unloaded.scriptFile, StandardCharsets.UTF_8));
            if (result instanceof Plugin plugin) {
                if (plugin.getId() == null) {
                    plugin.setId(id);
                } else if (!plugin.getId().equals(id)) {
                    throw new PluginException("Groovy plugin overrides getId(): script file is " + unloaded.scriptFile +
                            ", getId() returns " + plugin.getId());
                }
                register(plugin, unloaded.pluginConfig, id);
            } else {
                logger.warn("Groovy script " + unloaded.scriptFile + " does not evaluate to a Plugin instance; ignoring.");
            }
        } catch (Exception e) {
            logger.error("Error loading groovy plugin " + unloaded.scriptFile, e);
        }
    }

    private static void register(Plugin plugin, BLConfigPlugins pluginConfig, String scriptFileName) {
        ensureInitialized();
        for (Class<? extends Plugin> pluginClass: pluginTypes) {
            if (pluginClass.isInstance(plugin)) {
                type(pluginClass).register(pluginClass.cast(plugin), pluginConfig, scriptFileName);
            }
        }
    }

    /** Get the manager for one type of plugins. */
    @SuppressWarnings("unchecked")
    public static <T extends Plugin> PluginsOfType<T> type(Class<T> pluginType) {
        ensureInitialized();
        PluginsOfType<T> tPluginsOfType = (PluginsOfType<T>) pluginsByType.get(pluginType);
        if (tPluginsOfType == null)
            throw new IllegalArgumentException("Unknown plugin type: " + pluginType.getName());
        return tPluginsOfType;
    }

}
