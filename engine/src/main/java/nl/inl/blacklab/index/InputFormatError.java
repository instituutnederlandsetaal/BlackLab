package nl.inl.blacklab.index;

import org.apache.commons.lang3.StringUtils;

import nl.inl.util.FileReference;

/**
 * Description of a supported input format that is configuration-based.
 */
public class InputFormatError implements InputFormat {

    private final String formatIdentifier;

    private final String errorMessage;

    @Override
    public String getIdentifier() {
        return formatIdentifier;
    }

    @Override
    public String getDisplayName() {
        return formatIdentifier + " (error)";
    }

    @Override
    public String getDescription() {
        return "There was an error loading the format '" +
                getIdentifier() + "': " + getErrorMessage();
    }

    @Override
    public String getHelpUrl() {
        return "";
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    public InputFormatError(String formatIdentifier, String errorMessage) {
        assert !StringUtils.isEmpty(formatIdentifier);
        this.formatIdentifier = formatIdentifier;
        this.errorMessage = errorMessage;
    }

    @Override
    public DocIndexer createDocIndexer(DocWriter indexer, FileReference file) {
        throw new RuntimeException(getDescription());
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "(error with format " + getIdentifier() + ": " + getErrorMessage() + ")";
    }
}
