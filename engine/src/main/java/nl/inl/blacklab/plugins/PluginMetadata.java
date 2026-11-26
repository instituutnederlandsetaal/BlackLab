package nl.inl.blacklab.plugins;

import java.util.Comparator;
import java.util.List;

import nl.inl.blacklab.plugins.param.PluginParam;

/** Plugin information available after initialization, without preparing the plugin for use. */
public record PluginMetadata(String id, String name, String description, List<PluginParam> parameters) {

    public PluginMetadata {
        parameters = List.copyOf(parameters);
    }

    PluginMetadata(Plugin plugin) {
        this(plugin.getId(), plugin.getName(), plugin.getDescription(),
                plugin instanceof QueryFunction qf ? qf.getParameters() :
                        plugin.descriptor().getParams().values().stream()
                                .sorted(Comparator.comparing(PluginParam::name)).toList());
    }
}
