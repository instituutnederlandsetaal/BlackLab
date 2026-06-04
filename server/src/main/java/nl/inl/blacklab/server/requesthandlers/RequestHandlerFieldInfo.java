package nl.inl.blacklab.server.requesthandlers;

import java.util.Map;

import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.requests.RequestFieldInfo;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WsParam;

/**
 * Get information about a field in the index.
 */
public class RequestHandlerFieldInfo extends RequestHandler {

    public RequestHandlerFieldInfo(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.FIELD_INFO);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // Because reindexing might change something
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        int i = urlPathInfo.indexOf('/');
        String fieldName = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        if (fieldName.isEmpty()) {
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Bad URL. Either specify a field name to show information about, or remove the 'fields' part to get general index information.");
        }
        qpar = qpar.withOverrides(Map.of(WsParam.FIELD, fieldName));

        RequestFieldInfo request = RequestFieldInfo.fromParams(qpar);
        WebserviceRequestHandler.opFieldInfo(request, rs);

        // Remove any empty settings
        //response.removeEmptyMapValues();

        return HTTP_OK;
    }

}
