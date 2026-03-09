package nl.inl.blacklab.server.requesthandlers;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Request a JSON schema, e.g. input format files.
 */
public class RequestHandlerSchema extends RequestHandler {

    public RequestHandlerSchema(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.SCHEMA);
    }

    @Override
    public DataFormat getOverrideType() {
        return includesSchemaName() ? DataFormat.JSON : null; // schema only supports JSON
    }

    private boolean includesSchemaName() {
        return !StringUtils.isEmpty(urlResource);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        if (includesSchemaName()) {
            // /schema/NAME: return this schema
            WebserviceRequestHandler.opSchema(urlResource, rs);
        } else {
            // /schema: show available schemas
            WebserviceRequestHandler.opListSchemas(rs);
        }
        return HTTP_OK;
    }

}
