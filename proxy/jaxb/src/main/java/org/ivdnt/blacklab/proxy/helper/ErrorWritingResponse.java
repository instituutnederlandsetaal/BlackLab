package org.ivdnt.blacklab.proxy.helper;

public class ErrorWritingResponse extends RuntimeException {

    public ErrorWritingResponse(String message, Throwable cause) {
        super(message, cause);
    }

    public ErrorWritingResponse(Throwable cause) {
        super(cause);
    }

    public ErrorWritingResponse(String message) {
        super(message);
    }
}
