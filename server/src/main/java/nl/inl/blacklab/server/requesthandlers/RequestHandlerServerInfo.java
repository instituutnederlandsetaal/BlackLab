package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.requests.RequestServerInfo;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WsParam;

/**
 * Get information about this BlackLab server.
 */
public class RequestHandlerServerInfo extends RequestHandler {

    public RequestHandlerServerInfo(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.SERVER_INFO);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // You can create/delete indices, don't cache the list
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        RequestServerInfo request = RequestServerInfo.fromParams(indexMan, user,
                qpar.getBool(WsParam.INCLUDE_CUSTOM_INFO),
                debugMode);
        WebserviceRequestHandler.opServerInfo(request, rs);
        return HTTP_OK;
    }

}
