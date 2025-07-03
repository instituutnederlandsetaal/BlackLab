package nl.inl.blacklab.exceptions;

/**
 * Thrown when the file you're indexing is malformed in some way (i.e. not
 * well-formed XML)
 */
public class MalformedInputFile extends RuntimeException {

    public MalformedInputFile() {
        super();
    }

    public MalformedInputFile(String message, Throwable cause) {
        super(message, cause);
    }

    public MalformedInputFile(String message) {
        super(message);
    }

    public MalformedInputFile(Throwable cause) {
        super(cause);
    }

}
