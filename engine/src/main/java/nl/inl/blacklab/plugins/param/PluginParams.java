package nl.inl.blacklab.plugins.param;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Validated plugin parameters. */
public class PluginParams {
    public static final PluginParams NONE = new PluginParams(Map.of());

    private Map<String, Object> params;

    public PluginParams(Map<String, Object> params) {
        this.params = params;
    }

    public Optional<Object> getObject(PluginParam param) {
        return Optional.ofNullable(params.get(param.name()));
    }

    public Object getObject(PluginParam param, Object defaultValue) {
        return getObject(param).orElse(defaultValue);
    }

    public Optional<String> getString(PluginParam param) {
        if (!(param instanceof PString) && !(param instanceof PEnum))
            throw new IllegalArgumentException(param.name() + " is not declared as a string or enum");
        Optional<Object> value = getObject(param);
        return value.map(Object::toString);
    }

    public String getString(PluginParam param, String defaultValue) {
        return getString(param).orElse(defaultValue);
    }

    public Optional<Long> getInteger(PluginParam param) {
        if (!(param instanceof PInteger))
            throw new IllegalArgumentException(param.name() + " is not declared as an integer");
        Optional<Object> value = getObject(param);
        if (value.isEmpty())
            return Optional.empty();
        if (value.get() instanceof Number)
            return Optional.of((Long)value.get());
        throw new IllegalArgumentException(param.name() + " has a non-numeric value: " + value);
    }

    public long getInteger(PluginParam param, long defaultValue) {
        return getInteger(param).orElse(defaultValue);
    }

    public Optional<Double> getFloat(PluginParam param) {
        if (!(param instanceof PFloat))
            throw new IllegalArgumentException(param.name() + " is not declared as a float");
        Optional<Object> value = getObject(param);
        if (value.isEmpty())
            return Optional.empty();
        if (value.get() instanceof Number)
            return Optional.of((Double)value.get());
        throw new IllegalArgumentException(param.name() + " has a non-numeric value: " + value);
    }

    public double getFloat(PluginParam param, double defaultValue) {
        return getFloat(param).orElse(defaultValue);
    }

    public Optional<Boolean> getBoolean(PluginParam param) {
        Optional<Object> value = getObject(param);
        if (param instanceof PBoolean pBool) {
            if (value.isEmpty())
                return Optional.empty();
            if (value.get() instanceof Boolean b)
                return Optional.of(b);
        }
        throw new IllegalArgumentException("Parameter " + param.name() + " doesn't have a boolean value: " + value);
    }

    public boolean getBoolean(PluginParam param, boolean defaultValue) {
        return getBoolean(param).orElse(defaultValue);
    }

    public Optional<List<?>> getList(PluginParam param) {
        if (!(param instanceof PList))
            throw new IllegalArgumentException(param.name() + " is not declared as a list");
        Optional<Object> value = getObject(param);
        if (value.isEmpty())
            return Optional.empty();
        if (value.get() instanceof List<?> l) {
            return Optional.of(l);
        }
        throw new IllegalArgumentException("Parameter " + param.name() + " doesn't have a list value: " + value);
    }

    public List<?> getList(PluginParam param, List<?> defaultValue) {
        return getList(param).orElse(defaultValue);
    }

    public Optional<Map<String, String>> getStringStringMap(PluginParam param) {
        if (!(param instanceof PStringStringMap))
            throw new IllegalArgumentException(param.name() + " is not declared as a string map");
        Optional<Object> value = getObject(param);
        if (value.isEmpty())
            return Optional.empty();
        if (value.get() instanceof Map<?, ?> valueMap) {
            // Ensure that all keys and values are strings
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<?, ?> entry: valueMap.entrySet()) {
                result.put(entry.getKey().toString(), entry.getValue().toString());
            }
            return Optional.of(result);
        }
        throw new IllegalArgumentException("Parameter " + param.name() + " doesn't have a string-to-string map value: " + value);
    }

    public Map<String, String> getStringStringMap(PluginParam param, Map<String, String> defaultValue) {
        return getStringStringMap(param).orElse(defaultValue);
    }

    public boolean containsParam(PluginParam parToAddFile) {
        return params.containsKey(parToAddFile.name());
    }
}
