package nl.inl.blacklab.plugins.param;

public class InvalidPluginParameters extends RuntimeException {
    public InvalidPluginParameters(String msg) {
        super(msg);
    }

    public InvalidPluginParameters withParameterName(String name) {
        return new InvalidPluginParameters("Parameter '" + name + "': " + getMessage());
    }
}
