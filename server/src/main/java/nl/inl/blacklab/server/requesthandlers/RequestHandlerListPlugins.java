package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Get information about available plugins.
 */
public class RequestHandlerListPlugins extends RequestHandler {

    public RequestHandlerListPlugins(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.LIST_PLUGINS);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        WebserviceRequestHandler.opListPlugins(rs);
        return HTTP_OK;
    }

}
