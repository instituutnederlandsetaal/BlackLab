package nl.inl.blacklab.config;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.plugins.Plugin;

public class BLConfigPlugins {
    boolean delayInitialization = false;

    Map<String, Map<String, Object>> plugins = Collections.emptyMap();

    /**
     * Retrieve the configuration for a specific plugin.
     *
     * Tries to find the config by plugin ID first, then by simple class name,
     * then by fully qualified class name. If no config is found, returns an empty
     * map.
     *
     * @param plugin the plugin to get the config for
     * @return the config for that plugin
     */
    public Map<String, Object> get(Plugin plugin, String altId) {
        String id = plugin.getId();
        Map<String, Object> config = plugins.get(id);
        if (config == null && !StringUtils.isEmpty(altId)) {
            config = plugins.get(altId);
        }
        if (config == null && !plugin.getClass().isAnonymousClass()) {
            config = plugins.get(plugin.getClass().getSimpleName());
            if (config == null)
                config = plugins.get(plugin.getClass().getName());
        }
        if (config == null)
            config = Map.of();
        return config;
    }

    public boolean isDelayInitialization() {
        return delayInitialization;
    }

    @SuppressWarnings("unused")
    public void setDelayInitialization(boolean delayInitialization) {
        this.delayInitialization = delayInitialization;
    }

    public Map<String, Map<String, Object>> getPlugins() {
        return plugins;
    }

    @SuppressWarnings("unused")
    public void setPlugins(Map<String, Map<String, Object>> plugins) {
        if (plugins != null) {
            this.plugins = plugins;
        }
    }
}
