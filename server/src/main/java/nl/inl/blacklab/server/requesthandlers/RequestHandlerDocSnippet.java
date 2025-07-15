package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Get a snippet of a document's contents.
 */
public class RequestHandlerDocSnippet extends RequestHandler {
    public RequestHandlerDocSnippet(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.DOC_SNIPPET);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        determineDocPidFromPathInfo();
        WebserviceRequestHandler.opDocSnippet(params, rs);
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
