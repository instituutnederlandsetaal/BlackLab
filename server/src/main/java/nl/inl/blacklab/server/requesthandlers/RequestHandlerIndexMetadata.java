package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.requests.RequestCorpusInfo;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerIndexMetadata extends RequestHandler {

    public RequestHandlerIndexMetadata(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.CORPUS_INFO);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // because status might change (or you might reindex)
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        RequestCorpusInfo req = RequestCorpusInfo.fromParams(qpar);
        WebserviceRequestHandler.opCorpusInfo(req, rs);
        return HTTP_OK;
    }

}
