package nl.inl.blacklab.index;

import org.apache.commons.lang3.StringUtils;

/**
 * Description of a supported input format that is not configuration-based.
 */
public class InputFormatInfoClass implements InputFormatInfo {

    private final String formatIdentifier;

    private final InputFormat inputFormat;

    public InputFormatInfoClass(String formatIdentifier, InputFormat inputFormat) {
        assert !StringUtils.isEmpty(formatIdentifier);
        this.formatIdentifier = formatIdentifier;
        this.inputFormat = inputFormat;
    }

    @Override
    public String getIdentifier() {
        return formatIdentifier;
    }

    @Override
    public String getDisplayName() {
        return inputFormat.getClass().getSimpleName();
    }

    @Override
    public String getDescription() {
        return "The " + getDisplayName() + " indexer";
    }

    @Override
    public String getHelpUrl() {
        return "";
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public InputFormat getInputFormat() {
        return inputFormat;
    }

    @Override
    public String toString() {
        return "class-based input format '" + formatIdentifier +
                "' from class " + inputFormat.getClass().getName();
    }
}
