package nl.inl.blacklab.plugins;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import groovy.lang.GroovyShell;
import nl.inl.blacklab.config.BLConfigPlugins;
import nl.inl.blacklab.exceptions.PluginException;

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

    public static void addPluginType(Class<? extends Plugin> pluginType) {
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
    public static void initialize(BLConfigPlugins pluginConfig, File configDir) {
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
        File[] files = pluginsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
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

    private synchronized static void findGroovyScripts(BLConfigPlugins pluginConfig) {
        File[] scriptFiles = pluginsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".groovy"));
        if (scriptFiles != null) {
            for (File scriptFile: scriptFiles) {
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
        ArrayList<String> ids = new ArrayList<>(unloadedGroovyScripts.keySet());
        for (String id: ids) {
            getUnloaded(id, Plugin.class);
        }
    }

    /**
     * See if there's a groovy script with this name we can load.
     *
     * @param id plugin id (groovy script name)
     * @return plugin instance, if this script was found and could be read and compiled
     * @param <T> plugin type
     */
    static <T extends Plugin> Optional<T> getUnloaded(String id, Class<T> pluginType) {
        UnloadedGroovyPlugin unloaded;
        synchronized (unloadedGroovyScripts) {
            unloaded = unloadedGroovyScripts.remove(id);
        }
        if (unloaded == null)
            return Optional.empty();
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
                return Optional.of(pluginType.cast(plugin));
            } else {
                logger.warn("Groovy script " + unloaded.scriptFile + " does not evaluate to a Plugin instance; ignoring.");
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.error("Error loading groovy plugin " + unloaded.scriptFile, e);
            return Optional.empty();
        }
    }

    private static void register(Plugin plugin, BLConfigPlugins pluginConfig, String scriptFileName) {
        for (Class<? extends Plugin> pluginClass: pluginTypes) {
            if (pluginClass.isInstance(plugin)) {
                type(pluginClass).register(pluginClass.cast(plugin), pluginConfig, scriptFileName);
            }
        }
    }

    /** Get the manager for one type of plugins. */
    @SuppressWarnings("unchecked")
    public static <T extends Plugin> PluginsOfType<T> type(Class<T> pluginType) {
        if (!isInitialized)
            throw new IllegalStateException("Plugin system is not initialized.");
        PluginsOfType<T> tPluginsOfType = (PluginsOfType<T>) pluginsByType.get(pluginType);
        if (tPluginsOfType == null)
            throw new IllegalArgumentException("Unknown plugin type: " + pluginType.getName());
        return tPluginsOfType;
    }

}
