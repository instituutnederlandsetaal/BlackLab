package nl.inl.blacklab.index;

import java.io.File;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;

/**
 * Lazily-loaded info about a configuration-based input format.
 */
public class InputFormatInfoWithConfigLazy implements InputFormatInfo {

    /** Our id */
    private final String formatIdentifier;

    /** Our config file */
    private final File formatFile;

    /** Lazily-initialized input format info */
    InputFormatInfoWithConfig delegateInputFormat;

    /** Error, if reading the format file failed */
    private String errorMessage;

    public InputFormatInfoWithConfigLazy(String formatIdentifier, File formatFile) {
        delegateInputFormat = null;
        this.formatIdentifier = formatIdentifier;
        this.formatFile = formatFile;
    }

    InputFormatInfoWithConfig delegate() {
        if (delegateInputFormat == null) {
            try {
                delegateInputFormat = new InputFormatInfoWithConfig(formatIdentifier, formatFile);
            } catch (InvalidInputFormatConfig e) {
                errorMessage = e.getMessage();
            }
        }
        if (errorMessage != null)
            throw new IllegalStateException("Input format " + formatIdentifier + " is not valid: " + errorMessage);
        return delegateInputFormat;
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
        return delegate().getDisplayName();
    }

    @Override
    public String getDescription() {
        return delegate().getDescription();
    }

    @Override
    public String getHelpUrl() {
        return delegate().getHelpUrl();
    }

    @Override
    public boolean isVisible() {
        return delegate().isVisible();
    }

    @Override
    public synchronized ConfigInputFormat getConfig() {
        return delegate().getConfig();
    }

    @Override
    public synchronized InputFormat getInputFormat() {
        return delegate().getInputFormat();
    }

    @Override
    public String toString() {
        return delegateInputFormat != null ? delegateInputFormat.toString() :
                "config-based input format '" + formatIdentifier +
                "', will be loaded when needed from " + formatFile;
    }
}
