package nl.inl.blacklab.plugins.param;

public record PAny(
    String name,
    boolean isRequired,
    Validator validator
) implements PluginParam {

    public interface Validator {
        Validator DUMMY = (v) -> {};
        void validate(Object raw) throws InvalidPluginParameters;
    }

    public static PAny optional(String name, Validator validator) {
        return new PAny(name, false, validator);
    }

    public static PAny optional(String name) {
        return optional(name, Validator.DUMMY);
    }

    public static PAny required(String name, Validator validator) {
        return new PAny(name, true, validator);
    }

    public static PAny required(String name) {
        return required(name, Validator.DUMMY);
    }

    @Override
    public Object validate(Object raw) {
        try {
            validator.validate(raw);
        } catch (InvalidPluginParameters e) {
            throw new InvalidPluginParameters(msgNamePrefix() + "'value of any type' failed validation: " + e.getMessage());
        }
        return raw;
    }
}
