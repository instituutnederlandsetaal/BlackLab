package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.plugins.Plugin;
import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.blacklab.plugins.PluginsOfType;
import nl.inl.blacklab.plugins.param.PluginDescriptor;
import nl.inl.blacklab.plugins.param.PluginParam;

/**
 * Result object for listing available plugins grouped by type.
 */
public class ResultListPlugins {

    /**
     * Describes a single parameter of a plugin.
     */
    public static class PluginParamInfo {
        private final String name;
        private final String type;
        private final boolean required;

        PluginParamInfo(String name, String type, boolean required) {
            this.name = name;
            this.type = type;
            this.required = required;
        }

        public String getName() { return name; }

        public String getType() { return type; }

        public boolean isRequired() { return required; }
    }

    /**
     * Describes a single plugin instance.
     */
    public static class PluginInfo {
        private final String id;
        private final List<PluginParamInfo> params;

        PluginInfo(String id, List<PluginParamInfo> params) {
            this.id = id;
            this.params = params;
        }

        public String getId() { return id; }

        public List<PluginParamInfo> getParams() { return params; }
    }

    /** Plugins grouped by type name. */
    private final Map<String, List<PluginInfo>> pluginsByType;

    ResultListPlugins() {
        pluginsByType = new LinkedHashMap<>();
        for (Class<? extends Plugin> pluginType : PluginManager.getPluginTypes()) {
            @SuppressWarnings("unchecked")
            PluginsOfType<Plugin> manager = (PluginsOfType<Plugin>) PluginManager.type(pluginType);
            Collection<Plugin> plugins = manager.getAll();
            List<PluginInfo> pluginInfos = new ArrayList<>();
            for (Plugin plugin : plugins) {
                if (plugin.getId() == null)
                    continue; // skip plugins without an id (shouldn't happen normally)
                List<PluginParamInfo> paramInfos = new ArrayList<>();
                PluginDescriptor descriptor = plugin.descriptor();
                for (Map.Entry<String, PluginParam> entry : descriptor.getParams().entrySet()) {
                    PluginParam param = entry.getValue();
                    String typeName = paramTypeName(param);
                    paramInfos.add(new PluginParamInfo(param.name(), typeName, param.isRequired()));
                }
                pluginInfos.add(new PluginInfo(plugin.getId(), paramInfos));
            }
            pluginsByType.put(pluginType.getSimpleName(), pluginInfos);
        }
    }

    /**
     * Derive a human-readable type name for a plugin parameter.
     * Uses the simple class name of the parameter implementation, stripping the leading "P".
     * For example, {@code PString} becomes {@code "string"}, {@code PInteger} becomes {@code "integer"}.
     */
    private static String paramTypeName(PluginParam param) {
        String simpleName = param.getClass().getSimpleName();
        if (simpleName.length() > 1 && simpleName.charAt(0) == 'P' && Character.isUpperCase(simpleName.charAt(1))) {
            return Character.toLowerCase(simpleName.charAt(1)) + simpleName.substring(2);
        }
        return simpleName;
    }

    public Map<String, List<PluginInfo>> getPluginsByType() {
        return pluginsByType;
    }
}
