package nl.inl.blacklab.server.requesthandlers;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.fileupload2.core.FileItem;

import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Add or update an input format configuration.
 */
public class RequestHandlerAddFormat extends RequestHandler {

    public RequestHandlerAddFormat(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.WRITE_INPUT_FORMAT);
    }

    @Override
    public int handle(final ResponseStreamer rs) throws BlsException {
        debug(logger, "REQ add format: " + indexName);

        List<? extends FileItem<?>> files = FileUploadHandler.getFiles(request);
        if (files.size() != 1)
            throw new BadRequest("CANNOT_CREATE_INDEX",
                    "Adding a format requires the request to contain a single file in the 'data' field.");
        FileItem<?> file = files.get(0);
        if (!file.getFieldName().equals("data"))
            throw new BadRequest("CANNOT_CREATE_INDEX",
                    "Adding a format requires the request to contain a single file in the 'data' field.");

        String fileName = file.getName();
        InputStream fileInputStream;
        try {
            fileInputStream = file.getInputStream();
        } catch (IOException e) {
            throw new BadRequest("", e.getMessage());
        }
        WebserviceOperations.addUserFileFormat(params, fileName, fileInputStream);
        return Response.success(rs, "Format added.");
    }
}
