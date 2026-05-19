package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.requests.RequestRelations;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Autocompletion for metadata and annotated fields. Annotations must be
 * prefixed by the annotated field in which they exist.
 */
public class RequestHandlerRelations extends RequestHandler {

    public RequestHandlerRelations(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.RELATIONS);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        RequestRelations requestRelations = RequestRelations.fromParams(qpar);
        WebserviceRequestHandler.opRelations(requestRelations, rs);
        return HTTP_OK;
    }
}
