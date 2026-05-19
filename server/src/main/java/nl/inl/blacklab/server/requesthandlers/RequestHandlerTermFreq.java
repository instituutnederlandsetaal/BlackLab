package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.requests.RequestTermFrequencies;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Request handler for term frequencies for a set of documents.
 */
public class RequestHandlerTermFreq extends RequestHandler {

    public RequestHandlerTermFreq(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.TERM_FREQUENCIES);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        RequestTermFrequencies reqTermFreq = RequestTermFrequencies.fromParams(qpar);
        WebserviceRequestHandler.opTermFreq(reqTermFreq, rs);
        return HTTP_OK;
    }

}
