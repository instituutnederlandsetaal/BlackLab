package nl.inl.blacklab.index;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.indexers.config.InputFormatTypeBase;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.util.fileprocessor.FileReference;

/**
 * Represents an input file type and can index files of that type.
 * <p>
 * Implementations are thread-safe and reusable.
 */
public interface InputFormat {

    /**
     * Index documents contained in a file.
     *
     * @param docWriter where to write the documents
     * @param file the file to index
     * @throws ErrorIndexingFile if there was an error indexing the file
     */
    IndexerStats index(DocWriter docWriter, FileReference file) throws ErrorIndexingFile;

    /**
     * Index a specific document (inside our file).
     *
     * @param docWriter where to write the documents
     * @param file the file our document is in
     * @param documentPath XPath to the document inside the file
     * @param linkingDoc the document that called this method, if any (for access to metadata)
     * @param storeWithName store the document we're indexing under this field name. if null, store with the main annotated field
     */
    void indexSpecificDocument(DocWriter docWriter, FileReference file, String documentPath,
            InputFormatTypeBase.Doc linkingDoc,
            String storeWithName);
}
