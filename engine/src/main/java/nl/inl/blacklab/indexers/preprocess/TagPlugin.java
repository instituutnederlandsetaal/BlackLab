package nl.inl.blacklab.indexers.preprocess;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.Plugin;

public interface TagPlugin extends Plugin {
    default int getBufferSize() {
        return 251;
    }

    /**
     * Unfortunate side-effect of docIndexers requiring a filename to do their work.
     * 
     * @return a valid file name for the inputFile after tagging. E.g. something.xml -> something.tei.xml
     */
    String getOutputFileName(String inputFileName);

    /**
     * Return the output charset for the converted file.
     * @return the charset to use for the output file, or null if not a textual file.
     */
    Charset getOutputCharset();

    /**
     * Can this converter convert this file
     *
     * @param is stream containing a pushback buffer of at least 251 characters. If you need more, override getPushbackBufferSize() and return a higher number.
     * @param cs (optional) charset of the inputstream, if this is a text
     *            (non-binary) file type
     * @return true if this plugin wants to handle this file.
     */
    boolean canConvert(InputStream is, Charset cs, String filename);

    /**
     * Perform on a file.
     *
     * @param is input. Should not be closed by the implementation.
     * @param cs if the data came with a charset, it will be passed here. Otherwise, it will be null.
     * @param fileName fileName of the source file that's being converted.
     * @param os output. Should not be closed by the implementation.
     */
    void perform(InputStream is, Charset cs, String fileName, OutputStream os) throws PluginException;
}
