package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.requests.RequestCorpusStatus;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Get information about the status of an index.
 */
public class RequestHandlerIndexStatus extends RequestHandler {

    public RequestHandlerIndexStatus(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.CORPUS_STATUS);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // because status might change
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        WebserviceRequestHandler.opCorpusStatus(RequestCorpusStatus.fromParams(qpar), rs);
        return HTTP_OK;
    }

}
