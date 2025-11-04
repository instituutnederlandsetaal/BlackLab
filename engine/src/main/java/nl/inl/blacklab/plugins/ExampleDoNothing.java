package nl.inl.blacklab.plugins;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.util.fileprocessor.FileReference;

/** Simplest possible file converter that echoes the file back unchanged. */
public class ExampleDoNothing extends FileConverter {

    @Override
    public FileReference perform(FileReference input, String inputFormat) throws PluginException {
        try (Reader reader = input.getSinglePassReader()) {
            StringWriter writer = new StringWriter();
            IOUtils.copy(reader, writer);
            return FileReference.fromCharArray(input.getPath(), writer.toString().toCharArray(), input.getAssociatedFile());
        } catch (IOException e) {
            throw new PluginException(e);
        }
    }
}
