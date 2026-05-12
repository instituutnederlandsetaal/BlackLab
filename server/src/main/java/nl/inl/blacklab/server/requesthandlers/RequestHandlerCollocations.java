package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Convenient way to determine collocations (specific type of query/hits grouping combination).
 */
public class RequestHandlerCollocations extends RequestHandler {

    public RequestHandlerCollocations(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.COLLOCATIONS);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        WebserviceRequestHandler.opCollocations(params, rs, false);
        return HTTP_OK;
    }

}
