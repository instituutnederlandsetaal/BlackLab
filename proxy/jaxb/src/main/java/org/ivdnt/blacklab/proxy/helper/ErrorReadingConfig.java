package org.ivdnt.blacklab.proxy.helper;

public class ErrorReadingConfig extends RuntimeException {

    public ErrorReadingConfig(String message, Throwable cause) {
        super(message, cause);
    }

    public ErrorReadingConfig(Throwable cause) {
        super(cause);
    }

    public ErrorReadingConfig(String message) {
        super(message);
    }
}
