package nl.inl.blacklab.index;

import java.util.Optional;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.InputFormatType;
import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.blacklab.plugins.PluginsOfType;
import nl.inl.blacklab.plugins.param.PluginParams;

/**
 * Supports creation of several types of DocIndexers implemented directly in
 * code. Additionally will attempt to load classes if passed a fully-qualified
 * ClassName, and implementations by name in .indexers package within BlackLab.
 */
public class FinderInputFormatClass implements FinderInputFormat {

    @Override
    public InputFormatInfo find(String formatIdentifier) throws PluginException {
        PluginsOfType<InputFormatType> inputFormatPlugins = PluginManager.type(InputFormatType.class);
        Optional<InputFormatType> inputFormatType = inputFormatPlugins.getIfExists(formatIdentifier);
        if (inputFormatType.isEmpty())
            return null;
        InputFormat inputFormat = inputFormatType.get().createInputFormat(null, PluginParams.NONE);
        return DocumentFormats.add(formatIdentifier, inputFormat);
    }

}
