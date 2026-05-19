package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Clear the cache.
 */
public class RequestHandlerClearCache extends RequestHandler {
    public RequestHandlerClearCache(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.CACHE_CLEAR);
    }

    @Override
    public int handle(ResponseStreamer rs) {
        return WebserviceRequestHandler.opClearCache(searchMan.getBlackLabCache(), rs, debugMode);
    }

}
