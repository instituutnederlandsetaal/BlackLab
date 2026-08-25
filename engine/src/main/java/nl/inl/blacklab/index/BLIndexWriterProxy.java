package nl.inl.blacklab.index;

import java.io.IOException;

import org.apache.lucene.index.Term;
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
    void addDocument(BLInputDocument document) throws IOException;

    static void ensureDocTypeFieldSet(BLInputDocument document) {
        if (document.get(BLInputDocument.DOC_TYPE_FIELD_NAME) == null) {
            throw new ErrorIndexingFile("Document has no " + BLInputDocument.DOC_TYPE_FIELD_NAME +
                    " field; cannot add it to the index.");
        }
    }

    void close() throws IOException;

    void commit() throws IOException;

    void rollback() throws IOException;

    boolean isOpen();

    void deleteDocuments(Query q) throws IOException;

    long updateDocument(Term term, BLInputDocument document) throws IOException;

    /** Return number of documents modified (add/remove/update) so far */
    int getNumberOfDocs();
}
