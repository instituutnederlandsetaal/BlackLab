package nl.inl.blacklab.exceptions;

/** Something is wrong with the index. */
public class InvalidIndex extends RuntimeException {

    public InvalidIndex(String message) {
        super(message);
    }

    public InvalidIndex(String message, Throwable e) {
        super(message, e);
    }

    public InvalidIndex(Throwable e) {
        super(e);
    }

}
