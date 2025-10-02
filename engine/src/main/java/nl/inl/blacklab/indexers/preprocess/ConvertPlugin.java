package nl.inl.blacklab.indexers.preprocess;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.Plugin;

public interface ConvertPlugin extends Plugin {

    /**
     * Can this converter convert this file
     *
     * @param is stream containing a pushback buffer of at least 251 characters
     * @param cs (optional) charset of the inputstream, if this is a text
     *            (non-binary) file type
     * @return true if this file can be converted into this plugin's outputFormat
     */
    boolean canConvert(PushbackInputStream is, Charset cs, String inputFormat);

    /**
     * Perform on a text file.
     *
     * @param is input. Should not be closed by the implementation.
     * @param cs as inputFormat, but for the charset of the inputStream, not always
     *            meaningful, but required for some implementations that transform
     *            textual data.
     * @param inputFormat arbitrary string describing input data, can be file
     *            extension, or more semantic. Usage may vary, so acceptable values
     *            must be coordinated between callers and implementations.
     * @param os output. Should not be closed by the implementation.
     */
    void perform(InputStream is, Charset cs, String inputFormat, OutputStream os) throws PluginException;
}
