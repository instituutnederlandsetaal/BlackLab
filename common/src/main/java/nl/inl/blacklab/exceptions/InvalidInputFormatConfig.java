package nl.inl.blacklab.exceptions;

import java.io.File;

/**
 * Thrown when there's an error in the input format configuration.
 */
public class InvalidInputFormatConfig extends RuntimeException {

    public InvalidInputFormatConfig(String message) {
        super(message);
    }

    public InvalidInputFormatConfig(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidInputFormatConfig(Throwable cause) {
        super(cause);
    }

    File formatFile;

    String formatIdentifier;

    public static InvalidInputFormatConfig withFormatIdentifier(Exception e, String formatIdentifier) {
        InvalidInputFormatConfig result;
        if (e instanceof InvalidInputFormatConfig e2) {
            result = e2;
        } else {
            result = new InvalidInputFormatConfig(e.getMessage(), e);
        }
        result.setFormatIdentifier(formatIdentifier);
        return result;
    }

    public static InvalidInputFormatConfig withFormatFile(Exception e, File formatFile) {
        InvalidInputFormatConfig result;
        if (e instanceof InvalidInputFormatConfig e2) {
            result = e2;
        } else {
            result = new InvalidInputFormatConfig(e.getMessage(), e);
        }
        result.setFormatFile(formatFile);
        return result;
    }

    public void setFormatFile(File formatFile) {
        this.formatFile = formatFile;
    }

    public void setFormatIdentifier(String formatIdentifier) {
        this.formatIdentifier = formatIdentifier;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + where();
    }

    private String where() {
        if (formatFile != null)
            return " (in format file: " + formatFile + ")";
        if (formatIdentifier != null) {
            return " (in format: " + formatIdentifier + ")";
        }
        return "";
    }
}
