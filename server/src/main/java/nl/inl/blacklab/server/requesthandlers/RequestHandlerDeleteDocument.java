package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.webservice.WebserviceOperation;

/** Delete document from index */
public class RequestHandlerDeleteDocument extends RequestHandler {
    public RequestHandlerDeleteDocument(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.DELETE_DOCUMENT);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        if (indexName == null || indexName.isEmpty()) {
            return Response.badRequest(rs, "NO_INDEX_NAME",
                    "No index name specified. Specify a valid index name.");
        }
        try {
            debug(logger, "REQ delete document " + params.getDocPid() + " from index " + indexName);
            determineDocPidFromPathInfo();
            indexMan.getIndex(indexName).deleteDocumentByPid(params.getDocPid());
            return Response.status(rs, "SUCCESS", "Document deleted succesfully.", HTTP_OK);
        } catch (BlsException e) {
            throw e;
        } catch (Exception e) {
            return Response.internalError(rs, e, debugMode, "INTERR_DELETING_DOCUMENT");
        }
    }
}
