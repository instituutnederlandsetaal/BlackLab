package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.ResultDocContents;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Show (part of) the original contents of a document.
 */
public class RequestHandlerDocContents extends RequestHandler {

    public RequestHandlerDocContents(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.DOC_CONTENTS);
    }

    @Override
    public DataFormat getOverrideType() {
        // Application expects this MIME type, don't disappoint
        return DataFormat.XML;
    }

    @Override
    public boolean omitBlackLabResponseRootElement() {
        return true;
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException, InvalidQuery {
        determineDocPidFromPathInfo();
        ResultDocContents resultDocContents = WebserviceOperations.docContents(params);
        rs.docContentsResponsePlain(resultDocContents);
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }
}
