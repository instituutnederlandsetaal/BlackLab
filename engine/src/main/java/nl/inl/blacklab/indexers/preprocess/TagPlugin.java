package nl.inl.blacklab.indexers.preprocess;

import java.io.Reader;
import java.io.Writer;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.Plugin;

public interface TagPlugin extends Plugin {

    /**
     * Perform on a text file.
     *
     * @param reader input. Should not be closed by the implementation.
     * @param writer output. Should not be closed by the implementation.
     */
    void perform(Reader reader, Writer writer) throws PluginException;
}
