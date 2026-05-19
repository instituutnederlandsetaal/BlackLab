package nl.inl.blacklab.server.requesthandlers;

import java.util.List;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Get and change sharing options for a user corpus.
 */
public class RequestHandlerSharedWithMe extends RequestHandler {

    public RequestHandlerSharedWithMe(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.CORPUS_SHARING);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        debug(logger, "REQ shared-with-me: " + indexName);

        // Regular request: return the list of users this corpus is shared with
        List<String> sharedWithMe = WebserviceOperations.getCorporaSharedWithMe(user, indexMan);
        dstreamSharedWithMeResponse(rs, sharedWithMe);
        return HTTP_OK;
    }

    private void dstreamSharedWithMeResponse(ResponseStreamer responseWriter, List<String> corporaSharedWithMe) {
        DataStream ds = responseWriter.getDataStream();
        ds.startMap().startDynEntry("corpora").startList();
        for (String corpusId: corporaSharedWithMe) {
            ds.item("corpus", corpusId);
        }
        ds.endList().endDynEntry().endMap();
    }
}
