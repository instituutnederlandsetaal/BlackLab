package nl.inl.blacklab.plugins.param;

import java.util.List;

/** One of multiple possible types */
public record PMultiple(
    String name,
    boolean isRequired,
    List<PluginParam> options
) implements PluginParam {

    public static PMultiple optional(String name, List<PluginParam> options) {
        return new PMultiple(name, false, options);
    }

    public static PMultiple required(String name, List<PluginParam> options) {
        return new PMultiple(name, true, options);
    }

    @Override
    public Object validate(Object raw) {
        for (PluginParam option : options) {
            try {
                return option.validate(raw);
            } catch (InvalidPluginParameters e) {
                // Okay, just try the next option
            }
        }
        throw new InvalidPluginParameters(msgNamePrefix() + "doesn't match any of the options: " + raw);
    }
}
