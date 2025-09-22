package nl.inl.blacklab.index;

import java.util.Map;

import nl.inl.blacklab.exceptions.PluginException;

/**
 * Interface of converting a plugin (including using external services) Only a
 * single instance of a plugin is constructed, so plugins must be thread-safe.
 *
 * A plugin must define a no-argument constructor.
 */
public interface Plugin {

    /**
     * Read a value from our config if present.
     *
     * @param config root node of our config object
     * @param nodeName node to read
     * @return the value as a string
     * @throws PluginException on missing key or null value
     */
    static String configStr(Map<String, String> config, String nodeName) throws PluginException {
        String value = config.get(nodeName);
        if (value == null)
            throw new PluginException("Missing configuration value " + nodeName);

        return value;
    }

    /**
     * Return a globally unique id for this plugin class. This ID must be constant
     * across runs and versions.
     *
     * @return the global identifier for this plugin
     */
    String getId();

    /**
     * Return a user-friendly name for this plugin that can be used in messages,
     * etc.
     *
     * @return a user-friendly name for this plugin
     */
    String getDisplayName();

    String getDescription();

    /**
     * Initializes the plugin, called once after the initial loading of the class.
     *
     * @param config the config settings for this plugin
     */
    void init(Map<String, String> config) throws PluginException;
    
    /**
     * Does this plugin require configuration parameters?
     * 
     * If not, the plugin will always be initialized. If so, it will only
     * be initialized if configuration parameters were specified. 
     * 
     * @return true if parameters are required, false if not
     */
    boolean needsConfig();
}
