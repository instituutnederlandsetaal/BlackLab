package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Get information about supported input formats.
 */
public class RequestHandlerListInputFormats extends RequestHandler {

    private final boolean isXsltRequest;

    public RequestHandlerListInputFormats(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.LIST_INPUT_FORMATS);
        isXsltRequest = urlResource != null && !urlResource.isEmpty() && urlPathInfo != null
                && urlPathInfo.equals("xslt");
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // You can create/delete formats, don't cache the list
    }

    @Override
    public DataFormat getOverrideType() {
        // Application expects this MIME type, don't disappoint
        if (isXsltRequest)
            return DataFormat.XML;
        return super.getOverrideType();
    }

    @Override
    public boolean omitBlackLabResponseRootElement() {
        return isXsltRequest;
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        if (urlResource != null && !urlResource.isEmpty() && isXsltRequest) {
            params.setInputFormat(urlResource);
            WebserviceRequestHandler.opInputFormatXslt(params, rs);
        } else {
            if (urlResource != null && !urlResource.isEmpty()) {
                // Specific input format: either format information or XSLT request
                params.setInputFormat(urlResource);
                WebserviceRequestHandler.opInputFormatInfo(params, rs);
            } else {
                // Show list of supported input formats (for current user)
                WebserviceRequestHandler.opListInputFormats(params, rs, debugMode);
            }
        }
        return HTTP_OK;
    }

}
