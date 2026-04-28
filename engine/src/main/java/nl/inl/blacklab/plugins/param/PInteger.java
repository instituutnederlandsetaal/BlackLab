package nl.inl.blacklab.plugins.param;

public record PInteger(
    String name,
    boolean isRequired,
    long min,
    long max
) implements PluginParam {

    public static PInteger any(String name) {
        return any(name, false);
    }

    public static PInteger any(String name, boolean isRequired) {
        return new PInteger(name, isRequired, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public static PInteger range(String name, long min, long max) {
        return range(name, min, max, false);
    }

    public static PInteger range(String name, long min, long max, boolean isRequired) {
        return new PInteger(name, isRequired, min, max);
    }

    public static PluginParam nonnegative(String name, boolean isRequired) {
        return range(name, 0, Long.MAX_VALUE, isRequired);
    }

    @Override
    public Long validate(Object raw) {
        long v;
        if (raw instanceof Integer i)
            v = (long)i;
        else if (raw instanceof Long l)
            v = l;
        else if (raw instanceof Float f)
            v = (long)f.floatValue();
        else if (raw instanceof Double d)
            v = (long)d.doubleValue();
        else {
            try {
                v = Long.parseLong(raw.toString().trim());
            } catch (NumberFormatException e) {
                throw new InvalidPluginParameters(msgNamePrefix() + "not a valid long integer: " + raw);
            }
        }
        if (v < min || v > max)
            throw new InvalidPluginParameters(msgNamePrefix() + "value must be between " + min + " and " + max + ": " + raw);
        return v;
    }
}
