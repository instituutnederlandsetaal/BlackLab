package nl.inl.blacklab.server.exceptions;

import java.net.HttpURLConnection;

public class NotFound extends BlsException {

    public NotFound(String code, String msg) {
        super(HttpURLConnection.HTTP_NOT_FOUND, code, msg);
    }

}
