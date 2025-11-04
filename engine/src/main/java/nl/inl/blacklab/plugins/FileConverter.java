package nl.inl.blacklab.plugins;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.util.fileprocessor.FileReference;

/** Converts an input to an output stream.
 *
 * For example, this could convert a .docx file to an XML file to be indexed.
 */
public abstract class FileConverter extends Plugin {

    /**
     * Perform on a text file.
     *
     * @param input   input. Should not be closed by the implementation.
     * @param inputFormat arbitrary string describing input data, but usually file
     *                    extension. Some implementations may take this into account.
     * @return output file if successful
     * @throws PluginException on error, for example if the input format is not supported
     */
    public FileReference perform(FileReference input, String inputFormat) throws PluginException {
        throw new PluginException("Method not implemented");
    }
}
