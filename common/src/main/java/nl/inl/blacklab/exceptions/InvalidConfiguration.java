package nl.inl.blacklab.exceptions;

/**
 * Thrown when a configuration file is invalid.
 */
public class InvalidConfiguration extends RuntimeException {

    public InvalidConfiguration(String message) {
        super(message);
    }

    public InvalidConfiguration(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidConfiguration(Throwable cause) {
        super(cause);
    }

}
