package nl.inl.blacklab.server.requesthandlers;

import jakarta.servlet.http.HttpServletResponse;

import nl.inl.blacklab.server.exceptions.IllegalIndexName;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Show a static response such as an error or succes message.
 */
public class RequestHandlerStaticResponse extends RequestHandler {

    /** Error or status? */
    boolean isError = false;

    String code = null;

    String msg = null;

    int httpCode = HTTP_OK;

    /** If it's an internal error, this contains the exception */
    private Exception exception = null;

    /** If it's an internal error, this is nonempty */
    private String internalErrorCode = "";

    /**
     * Stream a simple status response.
     *
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param code (string) status code
     * @param msg the message
     * @param httpCode http status code to send
     * @return the request handler itself
     */
    public RequestHandlerStaticResponse status(String code, String msg, int httpCode) {
        this.code = code;
        this.msg = msg;
        this.httpCode = httpCode;
        return this;
    }

    /**
     * Stream a simple status response.
     *
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param code (string) BLS status code
     * @param msg the message
     * @param httpCode the HTTP status code to set
     * @return the request handler itself
     */
    public RequestHandlerStaticResponse error(String code, String msg, int httpCode) {
        this.code = code;
        this.msg = msg;
        this.httpCode = httpCode;
        isError = true;
        return this;
    }

    // Highest internal error code so far: 31

    public RequestHandlerStaticResponse internalError(Exception e, boolean debugMode, String code) {
        logger.debug("INTERNAL ERROR " + code + ":");
        e.printStackTrace();
        this.exception = e;
        this.debugMode = debugMode;
        internalErrorCode = code;
        httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        return this;
    }

    public RequestHandlerStaticResponse internalError(String message, boolean debugMode, String code) {
        logger.debug("INTERNAL ERROR " + code + ": " + message);
        msg = message;
        this.debugMode = debugMode;
        internalErrorCode = code;
        httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        return this;
    }

    public RequestHandlerStaticResponse unauthorized(String reason) {
        return error("NOT_AUTHORIZED", "Unauthorized operation. " + reason, HttpServletResponse.SC_UNAUTHORIZED);
    }

    public RequestHandlerStaticResponse methodNotAllowed(String method, String reason) {
        reason = reason == null ? "" : " " + reason;
        return error("ILLEGAL_REQUEST", "Illegal " + method + " request." + reason,
                HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    public RequestHandlerStaticResponse forbidden(String reason) {
        return error("FORBIDDEN_REQUEST", "Forbidden request. " + reason, HttpServletResponse.SC_FORBIDDEN);
    }

    public RequestHandlerStaticResponse badRequest(String code, String message) {
        return error(code, message, HttpServletResponse.SC_BAD_REQUEST);
    }

    public RequestHandler unknownOperation(String operationName) {
        return badRequest("UNKNOWN_OPERATION", "Unknown operation '" + operationName + "'. Check your URL.");
    }

    public RequestHandlerStaticResponse unavailable(String indexName, String status) {
        return error("INDEX_UNAVAILABLE", "The index '" + indexName + "' is not available right now. Status: " + status,
                HttpServletResponse.SC_CONFLICT);
    }

    public RequestHandlerStaticResponse illegalIndexName(String shortName) {
        return badRequest("ILLEGAL_INDEX_NAME", "\"" + shortName + "\" " + IllegalIndexName.ILLEGAL_NAME_ERROR);
    }

    RequestHandlerStaticResponse(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.STATIC_RESPONSE);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // error/status should never be cached, can change
    }

    @Override
    public int handle(ResponseStreamer rs) {
        if (internalErrorCode != null && internalErrorCode.length() > 0) {
            if (exception != null)
                rs.getDataStream().internalError(exception, debugMode, internalErrorCode);
            else if (msg != null)
                rs.getDataStream().internalError(msg, debugMode, internalErrorCode);
            else
                rs.getDataStream().internalError(internalErrorCode);
        } else if (isError) {
            rs.getDataStream().error(code, msg, null);
        } else {
            rs.getDataStream().statusObject(code, msg);
        }
        return httpCode;
    }

}
