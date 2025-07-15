package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Get information about a document.
 */
public class RequestHandlerDocInfo extends RequestHandler {

    public RequestHandlerDocInfo(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.DOC_INFO);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        determineDocPidFromPathInfo();
        debug(logger, "REQ doc info: " + indexName + "-" + params.getDocPid());
        WebserviceRequestHandler.opDocInfo(params, rs);
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
