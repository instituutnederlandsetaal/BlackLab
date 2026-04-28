package nl.inl.blacklab.plugins.param;

public record PBoolean(
    String name,
    boolean isRequired
) implements PluginParam {
    public static PBoolean optional(String name) {
        return new PBoolean(name, false);
    }

    public static PBoolean required(String name) {
        return new PBoolean(name, true);
    }

    @Override
    public Boolean validate(Object raw) {
        if (raw instanceof Boolean b)
            return b;
        String lowerCase = raw.toString().trim().toLowerCase();
        if (lowerCase.equals("true") || lowerCase.equals("false"))
            return Boolean.parseBoolean(lowerCase);
        throw new InvalidPluginParameters(msgNamePrefix() + "not a valid boolean integer: " + raw);
    }
}
