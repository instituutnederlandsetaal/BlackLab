package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Delete an input format configuration.
 */
public class RequestHandlerDeleteFormat extends RequestHandler {

    public RequestHandlerDeleteFormat(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.DELETE_INPUT_FORMAT);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        debug(logger, "REQ add format: " + indexName);
        WebserviceOperations.deleteUserFormat(user, indexMan, urlResource);
        return Response.success(rs, "Format deleted.");
    }

}
