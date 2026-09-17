package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import nl.inl.blacklab.indexers.config.WarnOnce;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.util.TextContent;

/**
 * Interface the DocIndexer gets to store documents.
 */
public interface DocWriter {

    IndexMetadataWriter metadata();

    BLIndexObjectFactory indexObjectFactory();
    
    /**
     * Add Lucene document(s) to the index.
     *
     * If multiple documents are added, they are added as a block (i.e. kept in a single segment).
     *
     * @param documents
     *            the documents to add
     */
    void addDocuments(List<BLInputDocument> documents) throws IOException;

    /**
     * Should we continue indexing or stop?
     *
     * We stop if we've reached the maximum that was set (if any),
     * or if a fatal error has occurred.
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

    Optional<Function<String, File>> linkedFileResolver();

    void storeInContentStore(BLInputDocument currentDoc, TextContent document, String contentStoreName);

    boolean needsPrimaryValuePayloads();

    default BlackLabIndex.IndexType getIndexType() {
        return metadata().getIndexType();
    }

    RelationsStrategy getRelationsStrategy();

    /** Force a merge (debug feature) */
    void debugForceMerge();

    /** Allows us to issue a warning that is only shown once. */
    WarnOnce warnOnce();
}
