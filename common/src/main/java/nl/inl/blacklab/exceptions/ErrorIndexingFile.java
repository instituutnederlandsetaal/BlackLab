package nl.inl.blacklab.exceptions;

/**
 * Thrown when the file you're indexing is malformed in some way (i.e. not
 * well-formed XML)
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
