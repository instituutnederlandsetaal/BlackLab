package nl.inl.blacklab.indexers.preprocess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.PluginException;

public class TagPluginNoop implements TagPlugin {
    
    @Override
    public boolean needsConfig() {
        return false;
    }

    @Override
    public String getId() {
        return "noop";
    }

    @Override
    public String getDisplayName() {
        return "NO OP";
    }

    @Override
    public String getDescription() {
        return "Passes through data without parsing";
    }

    @Override
    public void init(Map<String, String> config) {
        // NO OP
    }

    @Override
    public String getOutputFileName(String inputFileName) {
        return inputFileName;
    }

    @Override
    public Charset getOutputCharset() {
        return null;
    }

    @Override
    public boolean canConvert(InputStream is, Charset cs, String filename) {
        return false;
    }

    @Override
    public void perform(InputStream is, Charset cs, String fileName, OutputStream os) throws PluginException {
        try {
            IOUtils.copy(is, os);
        } catch (IOException e) {
            throw new PluginException(e);
        }
    }
}
