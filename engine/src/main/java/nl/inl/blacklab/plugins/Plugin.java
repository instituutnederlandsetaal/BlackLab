package nl.inl.blacklab.plugins;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.param.PluginDescriptor;
import nl.inl.blacklab.plugins.param.PluginParam;

/**
 * Interface of converting a plugin (including using external services) Only a
 * single instance of a plugin is constructed, so plugins must be thread-safe.
 *
 * A plugin must define a no-argument constructor.
 */
public abstract class Plugin {

    private Map<String, Object> config = Map.of();

    private File pluginDir = null;

    private String id = null;

    /**
     * Configure the plugin.
     * <p>
     * Called once after the initial loading of the class.
     *
     * @param config the config settings for this plugin
     * @param pluginDir where the plugin can find additional resources it needs. never null,
     *                  but the directory pointed to may not exist
     */
    public void configure(Map<String, Object> config, File pluginDir) {
        this.config = config;
        this.pluginDir = pluginDir;
    }

    private PluginDescriptor descriptor = new PluginDescriptor();

    public PluginParam addParam(PluginParam spec) {
        return descriptor.addParam(spec);
    }

    /** What parameters, if any, does this plugin take, and what are their types?
     * @return descriptor of the parameters this plugin takes
     */
    public PluginDescriptor descriptor() {
        return descriptor;
    }

    /**
     * Initializes the plugin.
     *
     * Called once after configure() has been called.
     */
    public void initialize() throws PluginException {
        // Nothing to initialize by default
    }

    /**
     * Return the short id for this plugin.
     * <p>
     * The id can be used to refer to the plugin in configuration files.
     * You can also use the class name.
     * <p>
     * Note that we ignore this method for scripted plugins (e.g. Groovy) because
     * they're often returned as anonymous classes, so the default doesn't make sense.
     * In this case, we use the script name (without extension) as the id.
     * <p>
     * Plugins can also be retrieved by their fully qualified class name and their
     * simple class name (if applicable, and in that order, after id has been checked).
     *
     * @return the short id for this plugin, or null if it has none
     */
    public String getId() {
        return id;
    }

    /**
     * Set the short ID of this plugin. Can only be set once.
     *
     * This is used to set a Groovy script's ID to the script name.
     *
     * @param id the ID to set
     */
    void setId(String id) {
        if (this.id != null)
            throw new IllegalStateException("Plugin ID can only be set once.");
        this.id = id;
    }

    /** Get the full configuration.
     *
     * @return configuration map
     */
    protected Map<String, Object> fullConfig() {
        return Collections.unmodifiableMap(config);
    }

    /** Get this plugin's directory.
     *
     * The plugin could read additional files it needs from here.
     *
     * @return the directory (never null, but may not exist)
     */
    protected File pluginDir() {
        return pluginDir;
    }

    /**
     * Read a string value from our config if present.
     *
     * @param name setting name
     * @return the value
     * @throws PluginException if not found or not a string
     */
    public String cfgString(String name) throws PluginException {
        String value = cfgString(name, null);
        if (value == null)
            throw new PluginException("Missing string configuration value: " + name);
        return value;
    }

    /**
     * Read a string value from our config, or a default value if not present.
     *
     * @param name setting name
     * @param defaultValue value to return if not present (may be null)
     * @return the value as a string
     * @throws PluginException if value was found but is not a string
     */
    public String cfgString(String name, String defaultValue) throws PluginException {
        Object value = config.get(name);
        if (value == null)
            value = defaultValue;
        if (value == null)
            return null;
        if (value instanceof String s)
            return s;
        throw new PluginException("Configuration value " + name + " is not a string");
    }

    /**
     * Read a string value from our config if present.
     *
     * @param name setting name
     * @return the value
     * @throws PluginException if not found or not an integer
     */
    public int cfgInt(String name) throws PluginException {
        Integer value = cfgInt(name, null);
        if (value == null)
            throw new PluginException("Missing integer configuration value: " + name);
        return value;
    }

    /**
     * Read an integer value from our config, or a default value if not present.
     *
     * @param name setting name
     * @param defaultValue value to return if not present (may be null)
     * @return the value as a string
     * @throws PluginException if value was found but is not a string
     */
    public Integer cfgInt(String name, Integer defaultValue) throws PluginException {
        Object value = config.get(name);
        if (value == null)
            value = defaultValue;
        if (value == null)
            return null;
        if (value instanceof Integer s)
            return s;
        throw new PluginException("Configuration value " + name + " is not an integer");
    }

    /**
     * Read a string value from our config if present.
     *
     * @param name setting name
     * @return the value
     * @throws PluginException if not found or not an integer
     */
    public long cfgLong(String name) throws PluginException {
        Long value = cfgLong(name, null);
        if (value == null)
            throw new PluginException("Missing long configuration value: " + name);
        return value;
    }

    /**
     * Read a long value from our config, or a default value if not present.
     *
     * @param name setting name
     * @param defaultValue value to return if not present (may be null)
     * @return the value
     * @throws PluginException if value was found but is not a long
     */
    public Long cfgLong(String name, Long defaultValue) throws PluginException {
        Object value = config.get(name);
        if (value == null)
            value = defaultValue;
        if (value == null)
            return null;
        if (value instanceof Long s)
            return s;
        throw new PluginException("Configuration value " + name + " is not a long");
    }

    /**
     * Read a boolean value from our config, or a default value if not present.
     *
     * @param name setting name
     * @param defaultValue value to return if not present (may be null)
     * @return the value as a string
     * @throws PluginException if value was found but is not a boolean or "true"/"false"
     */
    public boolean cfgBool(String name, boolean defaultValue) throws PluginException {
        Object value = config.get(name);
        if (value == null)
            return defaultValue;
        if (value instanceof Boolean b)
            return b;
        if (value instanceof String s) {
            if (s.equalsIgnoreCase("true"))
                return true;
            if (s.equalsIgnoreCase("false"))
                return false;
        }
        throw new PluginException("Configuration value " + name + " is not a boolean");
    }

    /**
     * Determine a file specified in our config, either absolute or relative to the pluginDir.
     *
     * The file may or may not exist; test using {@link File#exists()} if you need to know.
     *
     * @param name setting name
     * @param defaultFileName if setting not present, use this file name (relative to pluginDir)
     * @return a file reference, never null (although it may not exist)
     * @throws PluginException if value not found or not a string value
     * @deprecated renamed to {@link #cfgFile(String, String)}
     */
    @Deprecated
    public File cfgFileOptional(String name, String defaultFileName) throws PluginException {
        return cfgFile(name, defaultFileName);
    }

    /**
     * Find a file specified in our config, either absolute or relative to the pluginDir.
     *
     * The file must exist and be readable.
     *
     * @param name setting name
     * @param defaultFileName if setting not present, use this file name (relative to pluginDir)
     * @return file found (although it may not exist)
     * @throws PluginException if value not found, not a string, or not readable
     */
    public File cfgFile(String name, String defaultFileName) throws PluginException {
        String path = cfgString(name, defaultFileName);
        File file = new File(path);
        if (!file.exists())
            file = new File(pluginDir, path);
        return file;
    }

    /**
     * If specified in our config, find the file, either absolute or relative to the pluginDir.
     *
     * If specified, the file must exist and be readable.
     *
     * @param name setting name
     * @param mustExistIfSpecified check if the file exists?
     * @return file found (although it may not exist)
     * @throws PluginException if value not found, not a string, or not readable
     */
    public Optional<File> cfgFile(String name) throws PluginException {
        String path = cfgString(name, null);
        if (path == null)
            return Optional.empty();
        return Optional.of(new File(path));
    }

    /**
     * May this plugin safely be called by a BlackLab Server client?
     *
     * This method should return false, unless the plugin validates its input
     * to prevent any misuse, particularly if the plugin can access resources
     * on the filesystem or network.
     *
     * Plugins that don't declare themselves as web-safe may still be explicitly
     * whitelisted in blacklab-server.yaml, although doing so comes with
     * potential risks.
     *
     * @return true if the plugin may be called by a REST API client, false if not
     */
    public boolean isWebSafe() {
        return false;
    }

}
