package nl.inl.blacklab.server.lib;

import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;

/**
 * Different BLS responses with response code and message.
 */
public class Response {
    static final Logger logger = LogManager.getLogger(Response.class);

    private Response() {
    }

    /**
     * Stream a simple status response.
     *
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param rs output stream
     * @param code (string) status code
     * @param msg the message
     * @param httpCode http status code to send
     * @return the data object representing the error message
     */
    public static int status(ResponseStreamer rs, String code, String msg, int httpCode) {
        rs.getDataStream().statusObject(code, msg);
        return httpCode;
    }

    /**
     * Stream a simple status response.
     *
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param rs output stream
     * @param code (string) BLS status code
     * @param msg the message
     * @param httpCode the HTTP status code to set
     * @return the data object representing the error message
     */
    public static int error(ResponseStreamer rs, String code, String msg, Map<String, String> info, int httpCode) {
        rs.getDataStream().error(code, msg, info);
        return httpCode;
    }

    public static int error(ResponseStreamer rs, String code, String msg, Map<String, String> info, int httpCode, Throwable e) {
        rs.getDataStream().error(code, msg, info, e);
        return httpCode;
    }

    // Highest internal error code so far: 32

    public static int internalError(ResponseStreamer rs, Exception e, boolean debugMode, String code) {
        if (e.getCause() instanceof BlsException cause) {
            logger.warn("BLACKLAB EXCEPTION " + cause.getBlsErrorCode(), e);
            return Response.error(rs, cause.getBlsErrorCode(), cause.getMessage(), cause.getInfo(), cause.getHttpStatusCode());
        }
        logger.error("INTERNAL ERROR " + code + ":", e);
        rs.getDataStream().internalError(e, debugMode, code);
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    public static int success(ResponseStreamer rs, String msg) {
        return status(rs, "SUCCESS", msg, HttpServletResponse.SC_OK);
    }

    public static int forbidden(ResponseStreamer rs) {
        return error(rs, "FORBIDDEN_REQUEST", "Forbidden operation.", null,
                HttpServletResponse.SC_FORBIDDEN);
    }

    public static int forbidden(ResponseStreamer rs, String reason) {
        return error(rs, "FORBIDDEN_REQUEST", "Forbidden request. " + reason, null,
                HttpServletResponse.SC_FORBIDDEN);
    }

    public static int badRequest(ResponseStreamer rs, String code, String message) {
        return error(rs, code, message, null,HttpServletResponse.SC_BAD_REQUEST);
    }

}
