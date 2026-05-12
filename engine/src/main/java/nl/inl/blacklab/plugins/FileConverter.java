package nl.inl.blacklab.plugins;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.util.fileprocessor.FileReference;

/** Converts an input to an output stream.
 *
 * For example, this could convert a .docx file to an XML file to be indexed.
 */
public abstract class FileConverter extends Plugin {

    /** A FileConverter with any parameters it needs */
    public record Parameterized(FileConverter converter, PluginParams params) {
        public FileReference perform(FileReference result, String inputFormat) {
            return converter().perform(result, inputFormat, params());
        }
    }

    /** Extra converters to apply before and after the input format's default converters. */
    public record ExtraConverters(List<Parameterized> applyFirst, List<Parameterized> applyLast) {
        public static final ExtraConverters NONE = new ExtraConverters(List.of(), List.of());

        public static ExtraConverters fromConfig(List<Map<String, Object>> first, List<Map<String, Object>> last) {
            List<FileConverter.Parameterized> convFirst = first.stream().map(FileConverter::fromConfig).toList();
            List<FileConverter.Parameterized> convLast = last.stream().map(FileConverter::fromConfig).toList();
            return new FileConverter.ExtraConverters(convFirst, convLast);
        }

        public boolean isEmpty() {
            return applyFirst.isEmpty() && applyLast.isEmpty();
        }
    }

    public static Parameterized fromConfig(Map<String, Object> converterConfig) {
        PluginsOfType<FileConverter> fileConverters = PluginManager.type(FileConverter.class);
        String converterId = converterConfig.get("id").toString();
        FileConverter fileConverter = fileConverters.get(converterId);
        Map<String, Object> actualParams = new HashMap<>(converterConfig);
        actualParams.remove("id");
        PluginParams pluginParams = fileConverter.descriptor().validate(actualParams);
        return new Parameterized(fileConverter, pluginParams);
    }

    /**
     * Perform on a text file.
     *
     * @param input       input. Should not be closed by the implementation.
     * @param inputFormat arbitrary string describing input data, but usually file
     *                    extension. Some implementations may take this into account.
     * @param params
     * @return output file if successful
     * @throws PluginException on error, for example if the input format is not supported
     */
    public FileReference perform(FileReference input, String inputFormat, PluginParams params) throws PluginException {
        throw new PluginException("Method not implemented");
    }
}
