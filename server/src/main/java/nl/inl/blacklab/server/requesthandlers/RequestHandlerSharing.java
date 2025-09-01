package nl.inl.blacklab.server.requesthandlers;

import java.util.List;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Get and change sharing options for a user corpus.
 */
public class RequestHandlerSharing extends RequestHandler {

    public RequestHandlerSharing(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.CORPUS_SHARING);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        debug(logger, "REQ sharing: " + indexName);

        // If POST request with 'users' parameter: update the list of users to share with
        if (request.getMethod().equals("POST")) {
            String[] users = request.getParameterValues("users[]");
            if (users == null)
                users = new String[0];
            WebserviceOperations.setUsersToShareWith(params, users);
            return Response.success(rs, "Index shared with specified user(s).");
        }

        // Regular request: return the list of users this corpus is shared with
        List<String> shareWithUsers = WebserviceOperations.getUsersToShareWith(params);
        dstreamUsersResponse(rs, shareWithUsers);
        return HTTP_OK;
    }

    private void dstreamUsersResponse(ResponseStreamer responseWriter, List<String> shareWithUsers) {
        params.apiCompatibility();
        DataStream ds = responseWriter.getDataStream();
        ds.startMap().startDynEntry("users[]").startList();
        for (String userId : shareWithUsers) {
            ds.item("user", userId);
        }
        ds.endList().endDynEntry().endMap();
    }
}
