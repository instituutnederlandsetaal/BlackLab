package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.util.TextContent;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;

/**
 * Interface the DocIndexer gets to store documents.
 */
public interface DocWriter {

    IndexMetadataWriter metadata();

    BLIndexObjectFactory indexObjectFactory();
    
    /**
     * Add a Lucene document to the index
     *
     * @param document
     *            the document to add
     */
    void add(BLInputDocument document) throws IOException;

    /**
     * Should we continue indexing or stop?
     *
     * We stop if we've reached the maximum that was set (if any),
     * or if a fatal error has occurred (indicated by terminateIndexing).
     *
     * @return true if we should continue, false if not
     */
    boolean continueIndexing();

    /**
     * How many more documents should we process?
     *
     * @return the number of documents
     */
    int docsToDoLeft();

    File linkedFile(String inputFile);
    
    BLFieldType metadataFieldType(boolean tokenized);

    /**
     * Get our index listener, or create a console reporting listener if none was set yet.
     *
     * Also reports the creation of the Indexer and start of indexing, if it hadn't been reported
     * already.
     *
     * @return the listener
     */
    IndexListener listener();
    
    /**
     * Get the parameters we would like to be passed to the DocIndexer class.
     *
     * Used by DocIndexer classes to get their parameters.
     * @return the parameters
     */
    Map<String, String> indexerParameters();

    Optional<Function<String, File>> linkedFileResolver();

    /**
     * Add a field with its annotations to the forward index
     * 
     * @param field field to add
     * @param currentDoc Lucene doc, for storing the fiid
     */
    void addToForwardIndex(AnnotatedFieldWriter field, BLInputDocument currentDoc);

    void storeInContentStore(BLInputDocument currentDoc, TextContent document, String contentIdFieldName, String contentStoreName);

    boolean needsPrimaryValuePayloads();

    default BlackLabIndex.IndexType getIndexType() {
        return metadata().getIndexType();
    }

    RelationsStrategy getRelationsStrategy();
}
