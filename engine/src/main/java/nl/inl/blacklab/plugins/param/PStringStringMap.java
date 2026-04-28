package nl.inl.blacklab.plugins.param;

import java.util.HashMap;
import java.util.Map;

public record PStringStringMap(
        String name,
        boolean isRequired,
        Validator validator
) implements PluginParam {

    public interface Validator {
        Validator REASONABLE_LENGTHS = (l) -> {
            int MAX_KEY_LENGTH = 100;
            int MAX_VALUE_LENGTH = 500;
            for (Map.Entry<String, String> e: l.entrySet()) {
                if (e.getKey().length() > MAX_KEY_LENGTH) {
                    throw new InvalidPluginParameters("key '" + e.getKey() + "' is too long (max " + MAX_KEY_LENGTH + ")");
                }
                if (e.getValue().length() > MAX_VALUE_LENGTH) {
                    throw new InvalidPluginParameters("value for key '" + e.getKey() + "' is too long (max " + MAX_VALUE_LENGTH + ")");
                }
            }
        };

        void validate(Map<String, String> l) throws InvalidPluginParameters;
    }

    public static PStringStringMap optional(String name, Validator validator) {
        return new PStringStringMap(name, false, validator);
    }

    public static PStringStringMap required(String name, Validator validator) {
        return new PStringStringMap(name, true, validator);
    }

    @Override
    public Map<String, String> validate(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            // Ensure that all keys and values are strings
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(entry.getKey().toString(), entry.getValue().toString());
            }
            try {
                validator.validate(result);
            } catch (InvalidPluginParameters e) {
                throw new InvalidPluginParameters(msgNamePrefix() + "string-string map failed validation: " + e.getMessage());
            }
            return result;
        }
        throw new InvalidPluginParameters(msgNamePrefix() + "not a valid map of strings to strings: " + raw);
    }
}
