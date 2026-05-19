package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Display the contents of the cache.
 */
public class RequestHandlerCacheInfo extends RequestHandler {
    public RequestHandlerCacheInfo(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.CACHE_INFO);
    }

    @Override
    public boolean isCacheAllowed() {
        return false;
    }

    @Override
    public int handle(ResponseStreamer rs) {
        WebserviceRequestHandler.opCacheInfo(searchMan.getBlackLabCache(), qpar.isIncludeDebugInfo(), rs);
        return HTTP_OK;
    }

}
