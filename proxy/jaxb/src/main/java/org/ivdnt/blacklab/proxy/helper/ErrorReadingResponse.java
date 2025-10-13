package org.ivdnt.blacklab.proxy.helper;

public class ErrorReadingResponse extends RuntimeException {

    public ErrorReadingResponse(String message, Throwable cause) {
        super(message, cause);
    }

    public ErrorReadingResponse(Throwable cause) {
        super(cause);
    }

    public ErrorReadingResponse(String message) {
        super(message);
    }
}
