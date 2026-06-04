package nl.inl.blacklab.server.requesthandlers;

import java.util.List;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WsParam;

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
            Index index = indexMan.getIndex(qpar.getCorpusName());
            WebserviceOperations.setUsersToShareWith(user, index, users);
            return Response.success(rs, "Index shared with specified user(s).");
        }

        // Regular request: return the list of users this corpus is shared with
        Index index = indexMan.getIndex(qpar.getCorpusName());
        List<String> shareWithUsers = WebserviceOperations.getUsersToShareWith(user, index);
        dstreamUsersResponse(rs, shareWithUsers);
        return HTTP_OK;
    }

    private void dstreamUsersResponse(ResponseStreamer responseWriter, List<String> shareWithUsers) {
        ParamUtil.getApiVersion(qpar.get(WsParam.API)); // throws if too low
        DataStream ds = responseWriter.getDataStream();
        ds.startMap().startDynEntry("users[]").startList();
        for (String userId : shareWithUsers) {
            ds.item("user", userId);
        }
        ds.endList().endDynEntry().endMap();
    }
}
