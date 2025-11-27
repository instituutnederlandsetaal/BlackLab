package nl.inl.blacklab.index;

import java.io.File;

import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.InputFormatTypeConfig;

/**
 * Description of a supported input format that is configuration-based.
 */
public class InputFormatInfoWithConfig implements InputFormatInfo {

    /** Our configuration */
    private final ConfigInputFormat config;

    /** Our input format */
    private final InputFormat inputFormat;

    public InputFormatInfoWithConfig(ConfigInputFormat config) {
        assert config != null;
        this.config = config;
        inputFormat = InputFormatTypeConfig.fromConfig(getConfig());
    }

    public InputFormatInfoWithConfig(String formatIdentifier, File formatFile) {
        config = ConfigInputFormat.read(formatFile, formatIdentifier);
        inputFormat = InputFormatTypeConfig.fromConfig(config);
    }

    @Override
    public String getIdentifier() {
        return config.getName();
    }

    @Override
    public String getDisplayName() {
        return config.getDisplayName();
    }

    @Override
    public String getDescription() {
        return config.getDescription();
    }

    @Override
    public String getHelpUrl() {
        return config.getHelpUrl();
    }

    @Override
    public boolean isVisible() {
        return config.getIsVisible();
    }

    @Override
    public ConfigInputFormat getConfig() {
        return config;
    }

    @Override
    public InputFormat getInputFormat() {
        return inputFormat;
    }

    @Override
    public String toString() {
        File file = config.getReadFromFile();
        if (file == null)
            return "config-based input format '" + getIdentifier() + "' (no file reference)";
        return "config-based input format '" + getIdentifier() + "' (read from " + file + ")";
    }
}
