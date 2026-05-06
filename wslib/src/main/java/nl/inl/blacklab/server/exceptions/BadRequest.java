package nl.inl.blacklab.server.exceptions;

import java.net.HttpURLConnection;
import java.util.Map;

import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.exceptions.InvalidQuery;

public class BadRequest extends BlsException {

    public static @NonNull BadRequest pattSyntaxError(InvalidQuery invalidQuery) {
        return new BadRequest("PATT_SYNTAX_ERROR", invalidQuery.getMessage());
    }

    public BadRequest(String code, String msg) {
        this(code, msg, null, null);
    }

    public BadRequest(String code, String msg, Map<String, String> info) {
        this(code, msg, info, null);
    }

    public BadRequest(String code, String msg, Throwable cause) {
        this(code, msg, null, cause);
    }

    public BadRequest(String code, String msg, Map<String, String> info, Throwable cause) {
        super(HttpURLConnection.HTTP_BAD_REQUEST, code, msg, info, cause);
    }
}
