package nl.inl.blacklab.plugins.param;

public record PIntegerRange(
    String name,
    boolean isRequired,
    Validator validator
) implements PluginParam {

    public interface Validator {
        void validate(int[] range) throws InvalidPluginParameters;
    }

    public static PIntegerRange optional(String name, Validator validator) {
        return new PIntegerRange(name, false, validator);
    }

    public static PIntegerRange required(String name, Validator validator) {
        return new PIntegerRange(name, true, validator);
    }

    @Override
    public int[] validate(Object raw) {
        if (raw instanceof int[] arr && arr.length == 2) {
            try {
                validator.validate(arr);
                return arr;
            } catch (InvalidPluginParameters e) {
                throw new InvalidPluginParameters(msgNamePrefix() + "integer range failed validation: " + e.getMessage());
            }
        }
        throw new InvalidPluginParameters(msgNamePrefix() + "not a valid integer range: " + raw);
    }
}
