package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.DocIndexerConfig;
import nl.inl.blacklab.indexers.config.InputFormatReader;
import nl.inl.util.FileReference;

/**
 * Description of a supported input format that is configuration-based.
 */
public class InputFormatWithConfig implements InputFormat {

    private ConfigInputFormat config;

    private final String formatIdentifier;

    private final File formatFile;

    private String errorMessage;

    public InputFormatWithConfig(ConfigInputFormat config) {
        assert config != null;
        this.config = config;
        this.formatIdentifier = config.getName();
        this.formatFile = null;
    }

    public InputFormatWithConfig(String formatIdentifier, File formatFile) {
        config = null;
        this.formatIdentifier = formatIdentifier;
        this.formatFile = formatFile;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String getIdentifier() {
        return formatIdentifier;
    }

    @Override
    public String getDisplayName() {
        return getConfig().getDisplayName();
    }

    @Override
    public String getDescription() {
        return getConfig().getDescription();
    }

    @Override
    public String getHelpUrl() {
        return getConfig().getHelpUrl();
    }

    @Override
    public boolean isVisible() {
        return getConfig().isVisible();
    }

    @Override
    public synchronized ConfigInputFormat getConfig() {
        if (config == null) {
            try {
                config = new ConfigInputFormat(formatIdentifier);
                assert formatFile != null;
                config.setReadFromFile(formatFile);
                InputFormatReader.read(formatFile, config);
                config.validate();
            } catch (InvalidInputFormatConfig e) {
                errorMessage = e.getMessage();
                throw e;
            } catch (IOException e) {
                errorMessage = "Error reading input format config file " + formatFile + ": " + e.getMessage();
                throw new InvalidInputFormatConfig(errorMessage, e);
            }
        }
        assert config != null; // should never trigger, but SonarQube seems to believe it can
        return config;
    }

    @Override
    public DocIndexer createDocIndexer(DocWriter indexer, FileReference file) {
        DocIndexerConfig d = DocIndexerConfig.fromConfig(getConfig());
        d.setDocWriter(indexer);
        d.setDocumentName(file.getPath());
        d.setDocument(file);
        return d;
    }

    @Override
    public String toString() {
        File file = formatFile == null ? getConfig().getReadFromFile() : formatFile;
        if (file == null)
            return "(config-based input format without a file reference)";
        return "config-based input format '" + formatIdentifier + "' (read from " + file + ")";
    }
}
