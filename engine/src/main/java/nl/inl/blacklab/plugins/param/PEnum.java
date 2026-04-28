package nl.inl.blacklab.plugins.param;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public record PEnum(
    String name,
    boolean isRequired,
    Set<String> allowedValues
) implements PluginParam {

    public static PEnum of(String name, Collection<String> values) {
        return of(name, values, false);
    }

    public static PEnum of(String name, Collection<String> values, boolean required) {
        if  (values.isEmpty())
            throw new IllegalArgumentException("No enum values given");
        return new PEnum(name, required, new HashSet<>(values));
    }

    public static <T> PEnum of(String operation, Class<T> enumClass) {
        return of(operation, enumClass, false);
    }

    public static <T> PEnum of(String operation, Class<T> enumClass, boolean required) {
        // Get enum values and convert them to a set of strings
        Set<String> values = new HashSet<>();
        for (T constant : enumClass.getEnumConstants()) {
            values.add(constant.toString());
        }
        return new PEnum(operation, required, values);
    }

    public PEnum {
        assert allowedValues != null && !allowedValues.isEmpty();
    }

    @Override
    public Object validate(Object raw) {
        if (!(raw instanceof String rawStr))
            throw new InvalidPluginParameters(msgNamePrefix() + "must be a string: " + raw);
        if (!allowedValues.contains(raw))
            rawStr = rawStr.trim();
        if (!allowedValues.contains(rawStr))
            throw new InvalidPluginParameters(msgNamePrefix() + "value must be one of " + allowedValues + ": " + rawStr);
        return rawStr;
    }
}
