package nl.inl.blacklab.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.fileprocessor.FileReference;

public interface DocIndexer extends AutoCloseable {

    @Override
    void close();

    BLInputDocument getCurrentDoc();

    /**
     * Returns our DocWriter object
     *
     * @return the DocWriter object
     */
    DocWriter getDocWriter();

    /**
     * Get our index type, classic external or integrated.
     *
     * @return index type
     */
    default BlackLabIndex.IndexType getIndexType() {
        return getDocWriter().getIndexType();
    }

    /**
     * Set the DocWriter object.
     *
     * We use this to add documents to the index.
     *
     * Called by Indexer when the DocIndexer is instantiated.
     *
     * @param docWriter our DocWriter object
     */
    void setDocWriter(DocWriter docWriter);

    /**
     * Set the file name of the document to index.
     *
     * @param documentName name of the document
     */
    void setDocumentName(String documentName);

    /**
     * Set the document to index.
     *
     * @param file file to index
     * @throws FileNotFoundException if not found
     */
    void setDocument(FileReference file);

    /** Set the current document's directory.
     * This may e.g. be used to resolve XIncludes, e.g. by DocIndexerSaxon.
     */
    default void setDocumentDirectory(File dir) {}

    /**
     * Index documents contained in a file.
     *
     * @throws MalformedInputFile if the input file wasn't valid
     * @throws IOException if an I/O error occurred
     * @throws PluginException if an error occurred in a plugin
     */
    void index() throws IOException, MalformedInputFile, PluginException;

    boolean continueIndexing();

    List<String> getMetadataField(String name);

    void addMetadataField(String name, String value);

    /**
     * When all metadata values have been set, call this to add the to the Lucene document.
     *
     * We do it this way because we don't want to add multiple values for a field (DocValues and
     * Document.get() only deal with the first value added), and we want to set an "unknown value"
     * in certain conditions, depending on the configuration.
     */
    void addMetadataToDocument();

    /**
     * Report the amount of new characters processed since the last call
     */
    void reportCharsProcessed();

    /**
     * Report the amounf of new tokens processed since the last call
     */
    void reportTokensProcessed();

    /**
     * Keep track of how many tokens have been processed.
     */
    void documentDone(String documentName);

    /**
     * Keep track of how many tokens have been processed.
     */
    void tokensDone(int n);

    int numberOfDocsDone();

    long numberOfTokensDone();
}
