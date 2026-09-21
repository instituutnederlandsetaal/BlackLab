package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Show current server configuration.
 */
public class RequestHandlerConfig extends RequestHandler {

    public RequestHandlerConfig(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.CONFIG);
    }

    @Override
    public boolean isCacheAllowed() {
        return false;
    }

    @Override
    public int handle(ResponseStreamer rs) {
        WebserviceRequestHandler.opConfig(searchMan.config(), debugMode, rs);
        return HTTP_OK;
    }
}
