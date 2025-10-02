package nl.inl.blacklab.exceptions;

/**
 * Thrown when there was an error indexing a file.
 *
 * For example, if the file is malformed in some way (i.e. not
 * well-formed XML).
 */
public class ErrorIndexingFile extends RuntimeException {

    public ErrorIndexingFile() {
        super();
    }

    public ErrorIndexingFile(String message, Throwable cause) {
        super(message, cause);
    }

    public ErrorIndexingFile(String message) {
        super(message);
    }

    public ErrorIndexingFile(Throwable cause) {
        super(cause);
    }

}
