package nl.inl.blacklab.plugins;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.config.BLConfigPlugins;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.search.BlackLab;

/**
 * Manages plugins of one type.
 */
public class PluginsOfType<T extends Plugin> {

    private static final Logger logger = LogManager.getLogger(PluginsOfType.class);

    /**
     * a plugin id may only contain letters, numbers, dash, period and underscore
     */
    private static final Pattern PLUGIN_ID_PATTERN = Pattern.compile("[\\p{L}\\p{N}\\-._]+");

    /** Plugin type we're managing. */
    private final Class<T> pluginClass;

    /** Registered plugins of this type. */
    private final Set<PluginData<T>> plugins = new LinkedHashSet<>();

    /** Mapping from ids to plugins.
     *
     * Note that multiple ids may point to the same plugin,
     * e.g. [fully qualified] class name, script name, value returned by plugin.getId().
     * Usually you will use the script name for Groovy plugins and the class name for Java plugins.
     * plugin.getId() should only be used if you want to support a different id as well, e.g.
     * in the case of QueryFunctionAbs we want to refer to it as just "abs".
     */
    private final Map<String, PluginData<T>> idToPlugin = new HashMap<>();

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
                register(plugin, pluginConfig, null, true);
            } catch (ServiceConfigurationError e) {
                logger.error("Plugin failed to load: " + e.getMessage(), e);
            } catch (Exception e) {
                logger.error("Plugin {} failed to load: {}", (plugin == null ? "(unknown)" : plugin.getId()),
                                e.getMessage());
                logger.error(e);
            }
        }
    }

    void register(Plugin plugin, BLConfigPlugins configs, String alternateId, boolean registerClassName) {
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
        if (!plugin.getName().equals(id)) // e.g. function name, so "abs" for QueryFunctionAbs (unique for this plugin type)
            add(plugin.getName(), data);
        if (registerClassName && !plugin.getClass().isAnonymousClass()) {
            if (!plugin.getClass().getName().contains("$")) // skip e.g. "Script1$1"
                add(plugin.getClass().getName(), data);
            if (!plugin.getClass().getSimpleName().matches("\\d+")) // skip e.g. "1"
                add(plugin.getClass().getSimpleName(), data);
        }
    }

    public void add(String id, Plugin plugin) {
        PluginData<T> data = new PluginData<>((T) plugin, null, null);
        add(id, data);
    }

    private void add(String id, PluginData<T> data) {
        if (BlackLab.isPluginAllowed(data.getPlugin())) {
            synchronized (this) {
                if (!idToPlugin.containsKey(id)) {
                    idToPlugin.put(id.toLowerCase(), data);
                    plugins.add(data);
                } else if (!idToPlugin.get(id).equals(data)){
                    logger.warn("Plugin id collision, plugin '" + id + "' was already registered.");
                }
            }
        } else
            logger.warn("Skipping plugin '" + id + "'; it's not on the plugins.allowed list)");
    }

    public Collection<T> getAll() {
        PluginManager.loadAllGroovyScripts();
        List<T> result = new ArrayList<>();
        Collection<PluginData<T>> pluginDatas;
        synchronized (this) {
            pluginDatas = new ArrayList<>(plugins);
        }
        for (PluginData<T> data: pluginDatas) {
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
    public @NonNull T get(String id) throws PluginException {
        return getIfExists(id)
                .orElseThrow(() -> new IllegalArgumentException("Plugin id " + id + " not found."));
    }

    public boolean exists(String id) {
        return getIfExists(id).isPresent();
    }

    public Optional<T> getIfExists(String id) throws PluginException {
        PluginData<T> pluginData;
        synchronized (this) {
            pluginData = idToPlugin.get(id.toLowerCase());
        }
        if (pluginData == null) {
            // Maybe this is a groovy script we haven't loaded yet.
            PluginManager.getUnloaded(id);
            synchronized (this) {
                pluginData = idToPlugin.get(id.toLowerCase());
            }
        }
        if (pluginData == null) {
            return Optional.empty();
        }
        pluginData.initializePlugin();
        return Optional.of(pluginClass.cast(pluginData.getPlugin()));
    }

    /**
     * Used to initialize all plugins in one go.
     * <p>
     * Whether or not this is done depends on PluginManager config.
     */
    void initializePlugins() {
        Collection<PluginData<T>> pluginDatas;
        synchronized (this) {
            pluginDatas = new ArrayList<>(plugins);
        }
        pluginDatas.forEach(pluginData -> {
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
