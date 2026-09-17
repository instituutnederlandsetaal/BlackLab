package nl.inl.blacklab.index;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;

/**
 * Proxy for an IndexWriter object.
 *
 * This is necessary because in Solr mode, we don't directly write to the
 * IndexWriter; the proxy implementation will simply collect any document(s)
 * to be added, and they will eventually be handed over to Solr to be processed.
 */
public interface BLIndexWriterProxy {

    void addDocuments(List<BLInputDocument> documents) throws IOException;

    void deleteDocuments(Query q) throws IOException;

    long updateDocuments(Query q, List<BLInputDocument> document, boolean ignoreFragments) throws IOException;

    void close() throws IOException;

    void commit() throws IOException;

    void rollback() throws IOException;

    boolean isOpen();

    /** Return number of documents modified (add/remove/update) so far */
    int getNumberOfDocs();

}
