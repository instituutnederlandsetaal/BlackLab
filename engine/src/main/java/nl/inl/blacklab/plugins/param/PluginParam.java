package nl.inl.blacklab.plugins.param;

public interface PluginParam {

    String name();

    boolean isRequired();

    Object validate(Object raw) throws InvalidPluginParameters;

    default String msgNamePrefix() {
        return "Parameter '" + name() + "': ";
    }
}
