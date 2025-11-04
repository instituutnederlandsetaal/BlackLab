package nl.inl.blacklab.index;

import nl.inl.blacklab.exceptions.PluginException;

/**
 * Can find input formats at runtime.
 */
public interface FinderInputFormat {

    /**
     * Find a format.
     * <p>
     * Check isError() from the return value to make sure loading the format didn't fail.
     *
     * @return the format, or null if not found.
     */
    InputFormatInfo find(String formatIdentifier) throws PluginException;
}
