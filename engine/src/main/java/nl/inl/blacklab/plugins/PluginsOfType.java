package nl.inl.blacklab.plugins;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.config.BLConfigPlugins;
import nl.inl.blacklab.exceptions.PluginException;

/**
 * Manages plugins of one type.
 */
public class PluginsOfType<T extends Plugin> {

    private static final Logger logger = LogManager.getLogger(PluginsOfType.class);

    /**
     * a plugin id may only contain letters, numbers, dash, period and underscore
     */
    private static final Pattern PLUGIN_ID_PATTERN = Pattern.compile("[\\p{L}\\p{N}\\-._]+");

    final Class<T> pluginClass;

    /**
     * Plugins by their id
     */
    final Map<String, PluginData<T>> pluginsById = new HashMap<>();

    PluginsOfType(Class<T> pluginClass, BLConfigPlugins pluginConfig, URLClassLoader cl) {
        this.pluginClass = pluginClass;
        loadClasses(pluginConfig, cl);
    }

    private void loadClasses(BLConfigPlugins pluginConfig, ClassLoader cl) {
        Iterator<? extends Plugin> it = ServiceLoader.load(pluginClass, cl).iterator();
        while (it.hasNext()) {
            Plugin plugin = null;
            try {
                plugin = it.next();
                if (plugin.getId() == null)
                    plugin.setId(plugin.getClass().getSimpleName());
                //logger.info("Loading plugin {}", plugin);
                register(plugin, pluginConfig, null);
            } catch (ServiceConfigurationError e) {
                logger.error("Plugin failed to load: " + e.getMessage(), e);
            } catch (Exception e) {
                logger.error("Plugin {} failed to load: {}", (plugin == null ? "(unknown)" : plugin.getId()),
                                e.getMessage());
                logger.error(e);
            }
        }
    }

    void register(Plugin plugin, BLConfigPlugins configs, String alternateId) {
        String id = plugin.getId();
        if (id != null && !PLUGIN_ID_PATTERN.matcher(id).matches()) {
            logger.warn("Plugin id " + id + " (class " + plugin.getClass().getName() +
                    ") is not a valid id; ignoring this id.");
            id = null;
        }

        if (!pluginClass.isInstance(plugin)) {
            throw new IllegalArgumentException("Plugin " + plugin + " is not a " + pluginClass.getSimpleName()
                    + " (it's a " + plugin.getClass().getSimpleName() + ")");
        }

        // Add the plugin data to our maps.
        @SuppressWarnings("unchecked")
        PluginData<T> data = new PluginData<>((T) plugin, configs, alternateId);
        if (id != null)
            add(id, data);
        if (!StringUtils.isEmpty(alternateId) && !alternateId.equals(id))
            add(alternateId, data); // e.g. groovy script name without extension
        if (!plugin.getClass().isAnonymousClass()) {
            if (!plugin.getClass().getName().contains("$")) // skip e.g. "Script1$1"
                add(plugin.getClass().getName(), data);
            if (!plugin.getClass().getSimpleName().matches("\\d+")) // skip e.g. "1"
                add(plugin.getClass().getSimpleName(), data);
        }
    }

    private void add(String id, PluginData<T> data) {
        pluginsById.putIfAbsent(id.toLowerCase(), data);
    }

    public Collection<T> getAll() {
        PluginManager.loadAllGroovyScripts();
        List<T> result = new ArrayList<>();
        for (PluginData<T> data: pluginsById.values()) {
            T plugin = data.getPlugin();
            if (pluginClass.isInstance(plugin)) {
                try {
                    data.initializePlugin();
                    result.add(pluginClass.cast(plugin));
                } catch (PluginException e) {
                    // exception already cached in plugindata, no need to throw.
                    logger.error("Plugin {} failed to initialize: {}", plugin.getId(), e.getMessage());
                    logger.debug(e);
                }
            }
        }
        return result;
    }

    /**
     * Get a plugin by id, initializing it if necessary.
     * In addition to their id, plugins can also be retrieved by their fully
     * qualified class name and their simple class name (if applicable, and
     * in that order, after id has been checked).
     * For script plugins (e.g. Groovy) the id is always the script name without
     * the extension.
     *
     * @param id id of the plugin to get
     * @return the plugin
     * @throws PluginException when the plugin fails to initialize
     */
    public T get(String id) throws PluginException {
        Optional<T> plugin = getIfExists(id);
        if (plugin.isEmpty()) {
            // Could be a groovy script that was found, but hasn't been loaded yet. Load it now.
            plugin = PluginManager.getUnloaded(id, pluginClass);
        }
        return plugin.orElseThrow(() -> new IllegalArgumentException("Plugin id " + id + " not found."));
    }

    public boolean exists(String id) {
        return pluginsById.containsKey(id.toLowerCase());
    }

    public Optional<T> getIfExists(String id) throws PluginException {
        PluginData<T> pluginData = pluginsById.get(id.toLowerCase());
        if (pluginData == null)
            return Optional.empty();
        pluginData.initializePlugin();
        return Optional.of(pluginClass.cast(pluginData.getPlugin()));
    }

    /**
     * Used to initialize all plugins in one go.
     * <p>
     * Whether or not this is done depends on PluginManager config.
     */
    void initializePlugins() {
        pluginsById.values().forEach(pluginData -> {
            try {
                pluginData.initializePlugin();
            } catch (PluginException e) {
                // exception already cached in plugindata, no need to throw.
                logger.error("Plugin {} failed to initialize: {}", pluginData.getPlugin().getId(), e.getMessage());
                logger.debug(e);
            }
        });
    }
}
