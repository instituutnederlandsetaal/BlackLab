package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.requests.RequestParsePattern;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Autocompletion for metadata and annotated fields. Annotations must be
 * prefixed by the annotated field in which they exist.
 */
public class RequestHandlerParsePattern extends RequestHandler {

    public RequestHandlerParsePattern(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.PARSE_PATTERN);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        WebserviceRequestHandler.opParsePattern(RequestParsePattern.fromParams(qpar), rs);
        return HTTP_OK;
    }

}
