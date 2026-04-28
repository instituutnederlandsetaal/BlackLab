package nl.inl.blacklab.plugins;

import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.plugins.param.PluginParams;

/** Creates input formats of a certain type (e.g. XML-based) given a configuration.
 * <p>
 * Implementations are thread-safe and reusable.
 */
public abstract class InputFormatType extends Plugin {

    /** Create an actual input format with the given configuration.
     *
     * @param config input format configuration, or null if this is not a config-based format
     * @param params any parameters to customize the resulting input format (for future use, currently unused)
     * @return the input format
     */
    public abstract InputFormat createInputFormat(ConfigInputFormat config, PluginParams params);

}
