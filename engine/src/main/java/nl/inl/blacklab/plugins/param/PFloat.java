package nl.inl.blacklab.plugins.param;

public record PFloat(
    String name,
    boolean isRequired,
    double min,
    double max
) implements PluginParam {

    public static PFloat any(String name) {
        return any(name, false);
    }

    public static PFloat any(String name, boolean isRequired) {
        return new PFloat(name, isRequired, Double.MIN_VALUE, Double.MAX_VALUE);
    }

    public static PFloat range(String name, double min, double max) {
        return range(name, min, max, false);
    }

    public static PFloat range(String name, double min, double max, boolean isRequired) {
        return new PFloat(name, isRequired, min, max);
    }

    @Override
    public Double validate(Object raw) {
        double v;
        if (raw instanceof Integer i)
            v = (double)i;
        else if (raw instanceof Long l)
            v = (double)l;
        else if (raw instanceof Float f)
            v = (double)f;
        else if (raw instanceof Double d)
            v = d;
        else {
            try {
                v = Double.parseDouble(raw.toString().trim());
            } catch (NumberFormatException e) {
                throw new InvalidPluginParameters(msgNamePrefix() + "not a valid floating point value: " + raw);
            }
        }
        if (v < min || v > max)
            throw new InvalidPluginParameters(msgNamePrefix() + "value must be between " + min + " and " + max + ": " + raw);
        return v;
    }
}
