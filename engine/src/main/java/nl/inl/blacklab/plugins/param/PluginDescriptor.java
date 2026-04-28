package nl.inl.blacklab.plugins.param;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import nl.inl.blacklab.exceptions.PluginException;

/**
 * Describes a plugin, notably its parameters with type and validation rules.
 */
public final class PluginDescriptor {

    public static final PluginDescriptor NO_PARAMETERS = new PluginDescriptor(Map.of());

    public static PluginDescriptor of(PluginParam... params) {
        Map<String, PluginParam> paramsMap = new HashMap<>();
        for (PluginParam param: params) {
            paramsMap.put(param.name(), param);
        }
        return new PluginDescriptor(paramsMap);
    }

    private final Map<String, PluginParam> params;

    private boolean frozen;

    public PluginDescriptor() {
        this.params = new HashMap<>();
        this.frozen = false;
    }

    public PluginDescriptor(Map<String, PluginParam> params) {
        this.params = new HashMap<>(params);
        this.frozen = true;
    }

    public PluginParam addParam(PluginParam spec) {
        if (frozen)
            throw new PluginException("Cannot add parameter " + spec.name() + ", plugin descriptor is frozen");
        params.put(spec.name(), spec);
        return spec;
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isEmpty() {
        return params.isEmpty();
    }

    public PluginParams validate(Map<String, ?> rawInput) {
        if (!frozen)
            throw new PluginException("Cannot validate parameters, plugin descriptor is not frozen");

        if (params.isEmpty() && !rawInput.isEmpty())
            throw new InvalidPluginParameters("Plugin takes no parameters, but some were provided: " + rawInput);

        Map<String, Object> validated = new HashMap<>();
        for (PluginParam param: params.values()) {
            Object raw = rawInput.get(param.name());
            if (raw == null) {
                if (param.isRequired())
                    throw new InvalidPluginParameters("Missing required param: " + param.name());
                continue;
            }
            Object value = param.validate(raw);
            validated.put(param.name(), value);
        }

        // Reject unknown parameters not in the descriptor
        for (String key: rawInput.keySet()) {
            if (params.keySet().stream().noneMatch(p -> p.equals(key)))
                throw new InvalidPluginParameters("Unknown parameter: " + key);
        }

        return new PluginParams(validated);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (PluginDescriptor) obj;
        return Objects.equals(this.params, that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(params);
    }

    @Override
    public String toString() {
        return "PluginDescriptor[" +
                "params=" + params + ']';
    }

}
