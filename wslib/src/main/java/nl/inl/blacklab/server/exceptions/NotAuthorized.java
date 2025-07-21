package nl.inl.blacklab.server.exceptions;

import jakarta.servlet.http.HttpServletResponse;

public class NotAuthorized extends BlsException {

    public NotAuthorized(String msg) {
        super(HttpServletResponse.SC_UNAUTHORIZED, "NOT_AUTHORIZED", "Unauthorized operation. " + msg);
    }

}
