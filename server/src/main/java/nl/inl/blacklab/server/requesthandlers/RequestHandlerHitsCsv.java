package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.requests.RequestHits;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHitsCsv extends RequestHandler {

    public RequestHandlerHitsCsv(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.HITS_CSV);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException, InvalidQuery {
        WebserviceRequestHandler.opHits(RequestHits.fromParams(qpar, true, null), rs, true);
        return HTTP_OK;
    }

    @Override
    public DataFormat getOverrideType() {
        return DataFormat.CSV;
    }

}
