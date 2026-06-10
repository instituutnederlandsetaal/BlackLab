package nl.inl.blacklab.server.exceptions;

import java.net.HttpURLConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InternalServerError extends BlsException {
    static final Logger logger = LogManager.getLogger(InternalServerError.class);

    private final String internalErrorCode;

    public String getInternalErrorCode() {
        return internalErrorCode;
    }

    public InternalServerError(String code) {
        this("Internal error", code, null);
        logger.debug("INTERNAL ERROR " + internalErrorCode + " (no message)");
    }

    public InternalServerError(String msg, String internalErrorCode) {
        this(msg, internalErrorCode, null);
        logger.debug("INTERNAL ERROR " + internalErrorCode + ":" + msg);
    }

    public InternalServerError(String msg, String internalErrorCode, Throwable cause) {
        super(HttpURLConnection.HTTP_INTERNAL_ERROR, "INTERNAL_ERROR",
                msg + (cause == null ? "" : " (" + cause + ")"), cause);
        this.internalErrorCode = internalErrorCode;
        String optCausePrompt = cause == null ? " (no cause given): " : ": ";
        logger.error("INTERNAL ERROR {}{}{}", internalErrorCode, optCausePrompt, msg);
        if (cause != null)
            logger.error(cause);
    }

}
