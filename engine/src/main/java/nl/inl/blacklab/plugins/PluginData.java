package nl.inl.blacklab.plugins;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.config.BLConfigPlugins;
import nl.inl.blacklab.config.BlackLabConfig;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;

class PluginData<T extends Plugin> {

    private static final Logger logger = LogManager.getLogger(PluginData.class);

    /** Has plugin been initialized? */
    private boolean initialized;

    /** Exception during initialzation, if any */
    private PluginException initializationException;

    /** The plugin itself */
    private final T plugin;

    /** Plugin's config */
    private final Map<String, Object> config;

    /** Alternate id for the plugin (i.e. script name) */
    private final String altId;

    public T getPlugin() {
        return plugin;
    }

    public PluginData(T plugin, BLConfigPlugins configs, String altId) {
        this.plugin = plugin;
        this.altId = altId;
        File configFile = findConfigFile();
        if (configFile == null) {
            logger.debug("No config file found for plugin " + plugin.getId() + "; look in main BlackLab config file.");
            this.config = configs == null ? Map.of() : configs.get(plugin, altId);
        } else {
            logger.debug("Loading config for plugin " + plugin.getId() + " from " + configFile);
            try {
                // Load YAML or JSON file
                ObjectMapper mapper = Json.getYamlOrJsonMapper(configFile);
                this.config = mapper.readValue(new FileReader(configFile), new TypeReference<>() {
                });
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private File findConfigFile() {
        List<File> dir = List.of(PluginManager.getPluginsDir());
        List<String> names = Arrays.asList(plugin.getId(), altId, plugin.getClass().getName(),
                plugin.getClass().getSimpleName());
        return FileUtil.findFile(dir, names, BlackLabConfig.CONFIG_EXTENSIONS);
    }

    /**
     * Initialize the plugin, if it exists and is currently uninitialized.
     * Previously encountered errors are rethrown. If am error is encountered, it is
     * stored in the plugin data and rethrown.
     *
     * @throws PluginException when the plugin fails to initialize, care should be
     *                         taken by the caller to remove it from the list of plugins when
     *                         this occurs.
     */
    void initializePlugin() throws PluginException {
        synchronized (plugin) {
            if (initializationException != null)
                throw initializationException;
            if (initialized)
                return;

            try {
                logger.debug("Initializing plugin " + plugin.getId());
                plugin.configure(config, findPluginDir());
                plugin.initialize();
                plugin.descriptor().freeze();
                logger.debug("Initialized plugin " + plugin.getId());
                initialized = true;
            } catch (PluginException e) {
                initializationException = e;
                throw e;
            } catch (Throwable e) {
                initializationException = new PluginException("Error during initialization.", e);
                throw initializationException;
            }
        }
    }

    private File findPluginDir() {
        File file = null;
        File pluginsDir = PluginManager.getPluginsDir();
        if (altId != null)
            file = new File(pluginsDir, altId);
        if ((file == null || !file.exists()) && !plugin.getClass().isAnonymousClass()) {
            file = new File(pluginsDir, plugin.getClass().getSimpleName());
            if (!file.exists())
                file = new File(pluginsDir, plugin.getClass().getName());
        }
        // We do the "official" id last, so that if none of the directories exist,
        // this is the (non-existent) one passed to the plugin.
        if (file == null || !file.exists())
            file = new File(pluginsDir, plugin.getId());
        return file;
    }
}
