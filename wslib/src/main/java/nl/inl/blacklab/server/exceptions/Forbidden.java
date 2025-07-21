package nl.inl.blacklab.server.exceptions;

import jakarta.servlet.http.HttpServletResponse;

public class Forbidden extends BlsException {

    public Forbidden(String msg) {
        super(HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN_REQUEST", "Forbidden operation. " + msg);
    }

}
