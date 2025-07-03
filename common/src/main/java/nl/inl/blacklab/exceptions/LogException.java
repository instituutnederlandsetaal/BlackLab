package nl.inl.blacklab.exceptions;

/**
 * Error logging data.
 */
public class LogException extends RuntimeException {
    
    public LogException(String msg) {
        super(msg);
    }
    
    public LogException(String msg, Throwable e) {
        super(msg, e);
    }
    
    public LogException(Throwable e) {
        super(e);
    }
}
