package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.requests.RequestHits;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHits extends RequestHandler {

    public RequestHandlerHits(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.HITS);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException, InvalidQuery {
        WebserviceRequestHandler.opHits(RequestHits.fromParams(qpar, false, null), rs, false);
        return HTTP_OK;
    }

}
