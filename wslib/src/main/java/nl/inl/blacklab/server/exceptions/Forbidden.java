package nl.inl.blacklab.server.exceptions;

import java.net.HttpURLConnection;

public class Forbidden extends BlsException {

    public Forbidden(String msg) {
        super(HttpURLConnection.HTTP_FORBIDDEN, "FORBIDDEN_REQUEST", "Forbidden operation. " + msg);
    }

}
