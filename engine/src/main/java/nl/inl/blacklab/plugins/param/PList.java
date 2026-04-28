package nl.inl.blacklab.plugins.param;

import java.util.List;

import nl.inl.blacklab.search.lucene.BLSpanQuery;

public record PList(
    String name,
    boolean isRequired,
    Validator validator
) implements PluginParam {

    public interface Validator {
        Validator ALL_QUERIES = (l) -> {
            if (!l.stream().allMatch(item -> item instanceof BLSpanQuery))
                throw new InvalidPluginParameters("all items must be queries");
        };

        void validate(List<?> l) throws InvalidPluginParameters;
    }

    public static PList optional(String name, Validator validator) {
        return new PList(name, false, validator);
    }

    public static PList required(String name, Validator validator) {
        return new PList(name, true, validator);
    }

    @Override
    public List<?> validate(Object raw) {
        if (raw instanceof List<?> l) {
            try {
                validator.validate(l);
            } catch (InvalidPluginParameters e) {
                throw new InvalidPluginParameters(msgNamePrefix() + "list failed validation: " + e.getMessage());
            }
            return l;
        }
        throw new InvalidPluginParameters(msgNamePrefix() + "not a list: " + raw);
    }
}
