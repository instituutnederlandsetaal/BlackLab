package nl.inl.blacklab.server.exceptions;

import jakarta.servlet.http.HttpServletResponse;

public class ServiceUnavailable extends BlsException {

    public ServiceUnavailable(String msg) {
        super(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "SERVER_BUSY", msg);
    }

}
