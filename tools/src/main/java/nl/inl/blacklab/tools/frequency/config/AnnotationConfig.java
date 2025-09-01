package nl.inl.blacklab.tools.frequency.config;

/**
 * Config of an annotation in a frequency list.
 *
 * @param name       annotation name as in the blacklab index.
 * @param prettyName name used in output files. Defaults to 'name'.
 */
public record AnnotationConfig(String name, String prettyName) {
    public AnnotationConfig {
        if (prettyName == null)
            prettyName = name;
    }
}
