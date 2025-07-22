package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Delete a user index.
 */
public class RequestHandlerDeleteIndex extends RequestHandler {
    public RequestHandlerDeleteIndex(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.DELETE_CORPUS);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        if (indexName != null && !indexName.isEmpty()) {
            // Delete index
            try {
                debug(logger, "REQ delete index: " + indexName);
                indexMan.getIndex(indexName).blIndex(); // Make sure we're not deleting an index while it's in use.
                indexMan.deleteUserIndex(indexName);
                return Response.status(rs, "SUCCESS", "Index deleted succesfully.", HTTP_OK);
            } catch (BlsException e) {
                throw e;
            } catch (Exception e) {
                return Response.internalError(rs, e, debugMode, "INTERR_DELETING_INDEX_REQH");
            }
        }

        return Response.badRequest(rs, "CANNOT_CREATE_INDEX",
                "Could not delete index '" + indexName + "'. Specify a valid name.");
    }

}
