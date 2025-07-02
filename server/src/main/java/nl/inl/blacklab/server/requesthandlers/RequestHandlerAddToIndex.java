package nl.inl.blacklab.server.requesthandlers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.util.FileUtil;

/**
 * Add document(s) to a user index.
 */
public class RequestHandlerAddToIndex extends RequestHandler {

    public RequestHandlerAddToIndex(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.ADD_TO_CORPUS);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        debug(logger, "REQ add data: " + indexName);

        // Read uploaded files before checking for errors, or the client won't see our response :(
        // See https://stackoverflow.com/questions/18367824/how-to-cancel-http-upload-from-data-events/18370751#18370751
        List<FileItem> dataFiles = new ArrayList<>();
        Map<String, File> linkedFiles = new HashMap<>();
        try {
            for (FileItem f : FileUploadHandler.getFiles(request)) {
                switch (f.getFieldName()) {
                case "data":
                case "data[]":
                    dataFiles.add(f);
                    break;
                case "linkeddata":
                case "linkeddata[]":
                    String fileNameOnly = FilenameUtils.getName(f.getName());
                    File tempFile = FileUtil.createTempFileSafe(fileNameOnly); // Check that the temp file is actually in the temp dir.
                    tempFile.deleteOnExit();
                    try (OutputStream tempOut = new FileOutputStream(tempFile)) {
                        IOUtils.copy(f.getInputStream(), tempOut);
                    }
                    linkedFiles.put(fileNameOnly.toLowerCase(), tempFile);
                    break;
                }
            }
        } catch (IOException e) {
            throw new InternalServerError("Error occurred during indexing: " + e.getMessage(),
                    "INTERR_WHILE_INDEXING1");
        }

        // Convert dataFiles to a generic data structure
        final Iterator<WebserviceOperations.UploadedFile> dataFilesIt = new Iterator<>() {
            Iterator<FileItem> it = dataFiles.iterator();

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public WebserviceOperations.UploadedFile next() {
                FileItem df = it.next();
                return new WebserviceOperations.UploadedFile(df.getName(), df.get());
            }
        };

        String indexError = WebserviceOperations.addToIndex(params, dataFilesIt, linkedFiles);
        if (indexError != null)
            throw new BadRequest("INDEX_ERROR", "An error occurred during indexing. (error text: " + indexError + ")");
        return Response.success(rs, "Data added succesfully.");
    }

}
