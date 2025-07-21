package nl.inl.blacklab.server.exceptions;

import jakarta.servlet.http.HttpServletResponse;

public class NotFound extends BlsException {

    public NotFound(String code, String msg) {
        super(HttpServletResponse.SC_NOT_FOUND, code, msg);
    }

}
