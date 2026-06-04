package nl.inl.blacklab.server.requesthandlers;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.requests.RequestAutocomplete;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WsParam;

/**
 * Autocompletion for metadata and annotated fields. Annotations must be
 * prefixed by the annotated field in which they exist.
 */
public class RequestHandlerAutocomplete extends RequestHandler {

    public RequestHandlerAutocomplete(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.AUTOCOMPLETE);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // Because reindexing might change something
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        // Get field and (optionally) annotation name from URL
        String[] pathParts = StringUtils.split(urlPathInfo, '/');
        if (pathParts.length == 0)
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Bad URL. Specify a field name and optionally an annotation to autocomplete.");

        String fieldNameOrAnnotation = pathParts.length > 1 ? pathParts[1] : pathParts[0];
        if (fieldNameOrAnnotation.isEmpty()) {
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Bad URL. Specify a field name and optionally a annotation to autocomplete.");
        }
        String annotatedFieldName = pathParts.length > 1 ? pathParts[0] : null;
        String fieldName = StringUtils.isEmpty(annotatedFieldName) ? fieldNameOrAnnotation : annotatedFieldName;
        String annotationName = StringUtils.isEmpty(annotatedFieldName) ? "" : fieldNameOrAnnotation;

        Map<WsParam, Object> overrides = Map.of(WsParam.FIELD, fieldName,
                WsParam.ANNOTATION, annotationName);
        qpar = qpar.withOverrides(overrides);

        WebserviceRequestHandler.opAutocomplete(RequestAutocomplete.fromParams(qpar), rs);
        return HTTP_OK;
    }

}
