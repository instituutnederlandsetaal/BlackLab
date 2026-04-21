package nl.inl.blacklab.indexers.preprocess;

import java.io.IOException;
import java.io.Reader;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.util.StringUtil;
import nl.inl.util.fileprocessor.FileReference;

/** Test plugin that adds an XML comment at the end of the file. */
public class AddMessage extends FileConverter {

    private static final Logger logger = LogManager.getLogger(AddMessage.class);

    private String message;

    @Override
    public void initialize() throws PluginException {
        // no config needed
        message = cfgString("message", "Tagged by FileConverterTest");
        logger.info("initialize: message is {}", message);
    }

    @Override
    public synchronized FileReference perform(FileReference input, String inputFormat) throws PluginException {
        try (Reader reader = input.getSinglePassReader()) {
            String str = IOUtils.toString(reader);
            str = str.replace("</TEI>", "<!-- " + StringUtil.escapeQuote(message, "'") + " --></TEI>");
            logger.warn("perform: appended message {}", message);
            return FileReference.fromCharArray(input.getPath(), str.toCharArray(), input.getAssociatedFile());
        } catch (IOException e) {
            throw new PluginException("Error in TagPluginTest", e);
        }
    }
}
