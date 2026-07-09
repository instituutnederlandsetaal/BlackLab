import nl.inl.blacklab.plugins.FileConverter
import nl.inl.blacklab.plugins.param.PluginParams
import nl.inl.util.StringUtil
import nl.inl.util.fileprocessor.FileReference
import org.apache.commons.io.IOUtils

return new FileConverter() {
    String message

    FileReference perform(FileReference input, String format, PluginParams params) {
        try (def reader = input.getSinglePassReader()) {
            String str = IOUtils.toString(reader)
            System.err.println("Adding message to " + input.getPath())
            str = str.replaceAll("</TEI>", "<!-- " + StringUtil.escapeQuote(message, "'") + " --></TEI>")
            return FileReference.fromCharArray(input.getPath(), str.toCharArray(), input.getAssociatedFile())
        }
    }

    void initialize() {
        // Get the message from our YAML config file (or use default)
        message = cfgString("message", "Default groovy-plugin message")
        // If a message file exists in our config dir (plugins/, read the message from there instead
        def messageFile = cfgFileOptional("messageFile", "message.txt")
        if (messageFile.exists())
            message = IOUtils.toString(new FileReader(messageFile))
    }
}