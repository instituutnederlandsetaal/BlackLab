package nl.inl.blacklab.server.exceptions;

import java.net.HttpURLConnection;

public class NotAuthorized extends BlsException {

    public NotAuthorized(String msg) {
        super(HttpURLConnection.HTTP_UNAUTHORIZED, "NOT_AUTHORIZED", "Unauthorized operation. " + msg);
    }

}
