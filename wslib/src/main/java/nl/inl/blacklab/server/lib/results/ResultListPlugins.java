package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.plugins.AuthMethodProvider;
import nl.inl.blacklab.plugins.DocTaskType;
import nl.inl.blacklab.plugins.InputFormatType;
import nl.inl.blacklab.plugins.Plugin;
import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.blacklab.plugins.PluginsOfType;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PMultiple;
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
        private final List<String> options;

        PluginParamInfo(String name, String type, boolean required, List<String> options) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.options = options;
        }

        public String getName() { return name; }

        public String getType() { return type; }

        public boolean isRequired() { return required; }

        public List<String> getOptions() { return options; }
    }

    /**
     * Describes a single plugin instance.
     */
    public static class PluginInfo {

        private final String name;

        private final String description;

        private final Map<String, PluginParamInfo> paramsMap;

        PluginInfo(String name, String description, Map<String, PluginParamInfo> params) {
            this.name = name;
            this.description = description;
            this.paramsMap = params;
        }

        PluginInfo(String name, String description, List<PluginParamInfo> params) {
            this.name = name;
            this.description = description;
            this.paramsMap = new LinkedHashMap<>();
            for (PluginParamInfo p : params) {
                this.paramsMap.put(p.getName(), p);
            }
        }

        public String getName() { return name; }

        public String getDescription() { return description; }

        public Map<String, PluginParamInfo> getParamsMap() { return paramsMap; }
    }

    /** Plugins grouped by type name. */
    private final Map<String, List<PluginInfo>> pluginsByType;

    ResultListPlugins() {
        pluginsByType = new LinkedHashMap<>();
        for (Class<? extends Plugin> pluginType : PluginManager.getPluginTypes()) {
            if (pluginType == AuthMethodProvider.class || pluginType == InputFormatType.class ||
                pluginType == DocTaskType.class) {
                // Don't return these in BLS response; clients cannot use them.
                continue;
            }
            @SuppressWarnings("unchecked")
            PluginsOfType<Plugin> manager = (PluginsOfType<Plugin>) PluginManager.type(pluginType);
            Collection<Plugin> plugins = manager.getAll();
            List<PluginInfo> pluginInfos = new ArrayList<>();
            for (Plugin plugin : plugins) {
                if (plugin.getId() == null) {
                    // A plugin ID is normally set during registration (via its class simple name if not otherwise
                    // provided). A null ID could only occur if a plugin was registered without any ID, class name,
                    // or alternate name matching. This is defensive programming against such edge cases.
                    continue;
                }
                PluginDescriptor descriptor = plugin.descriptor();
                if (plugin instanceof QueryFunction qf){
                    // Query functions are special, they have a parameter list, not a map
                    List<PluginParamInfo> par = qf.getParameters().stream()
                            .map(p -> {
                                String typeName = paramTypeName(p);
                                List<String> opt = getMultipleTypeOptions(p);
                                return new PluginParamInfo(p.name(), typeName, p.isRequired(), opt);
                            })
                            .toList();
                    pluginInfos.add(new PluginInfo(plugin.getName(), plugin.getDescription(), par));
                } else {
                    // By default, plugins use named parameters (without a defined order)
                    // We use a treemap because we will output them as a list (because QueryFunction is a list,
                    // and we want the same structure for all plugins), and we want the response to be stable.
                    Map<String, PluginParamInfo> paramInfos = new TreeMap<>();
                    for (Map.Entry<String, PluginParam> entry : descriptor.getParams().entrySet()) {
                        PluginParam param = entry.getValue();
                        String typeName = paramTypeName(param);
                        List<String> opt = getMultipleTypeOptions(param);
                        paramInfos.put(param.name(), new PluginParamInfo(param.name(), typeName, param.isRequired(), opt));
                    }
                    pluginInfos.add(new PluginInfo(plugin.getName(), plugin.getDescription(), paramInfos));
                }
            }
            pluginsByType.put(pluginType.getSimpleName(), pluginInfos);
        }
    }

    private static @NonNull List<String> getMultipleTypeOptions(PluginParam p) {
        return p instanceof PMultiple pm ?
                pm.options().stream().map(ResultListPlugins::paramTypeName).toList() :
                List.of();
    }

    /**
     * Derive a human-readable type name for a plugin parameter.
     * <p>
     * All built-in parameter types follow the convention of being named with a leading "P" followed by
     * an uppercase letter (e.g., {@code PString}, {@code PInteger}, {@code PFloat}, {@code PBoolean}).
     * This method strips the leading "P" and lowercases the first letter to produce a clean type name
     * (e.g., {@code "string"}, {@code "integer"}, {@code "float"}, {@code "boolean"}).
     * If a parameter class does not follow this convention, the full simple class name is returned as-is.
     */
    private static String paramTypeName(PluginParam param) {
        if (param instanceof PMultiple) {
            return "choice";
        }
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
