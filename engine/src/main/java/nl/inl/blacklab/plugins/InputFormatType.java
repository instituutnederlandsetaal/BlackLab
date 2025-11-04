package nl.inl.blacklab.plugins;

import java.util.Map;

import nl.inl.blacklab.index.InputFormat;

/** Creates input formats of a certain type (e.g. XML-based) given a configuration.
 * <p>
 * Implementations are thread-safe and reusable.
 */
public abstract class InputFormatType extends Plugin {

    /** Create an actual input format with the given configuration.
     *
     * @param configuration configuration parameters
     * @return the input format
     */
    public abstract InputFormat createInputFormat(Map<String, Object> configuration);

}
