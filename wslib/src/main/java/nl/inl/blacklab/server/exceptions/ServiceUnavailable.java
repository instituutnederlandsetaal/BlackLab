package nl.inl.blacklab.server.exceptions;

import java.net.HttpURLConnection;

public class ServiceUnavailable extends BlsException {

    public ServiceUnavailable(String msg) {
        super(HttpURLConnection.HTTP_UNAVAILABLE, "SERVER_BUSY", msg);
    }

}
