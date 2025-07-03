package nl.inl.blacklab.index;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.ivdnt.blacklab.solr.BLSolrXMLLoader;

import nl.inl.blacklab.search.BlackLabIndexWriter;

/**
 * Simple proxy for Solr IndexWriter.
 * The SOLR implementation of IndexWriterProxy (i.e. this) 
 * doesn't do work itself, as we need the SolrQueryRequest in order to submit SolrInputDocuments.
 * And we don't have that here.
 * Also we might need to modify the solr schema to add new fields.
 * 
 * Instead, we save up the solr documents, and the topmost site where .index() was first called
 * can get the documents from us and process them independently.
 * See {@link BLSolrXMLLoader}
 */
public class BLIndexWriterProxySolr implements BLIndexWriterProxy, Closeable {
    BlackLabIndexWriter writer;

    List<BLInputDocumentSolr> pendingAddDocuments = new ArrayList<>();

    BLIndexWriterProxySolr(BlackLabIndexWriter writer) {
        this.writer = writer;
    }


    @Override
    public synchronized void addDocument(BLInputDocument document) throws IOException {
        pendingAddDocuments.add((BLInputDocumentSolr) document);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void commit() throws IOException {
    }

    @Override
    public void rollback() throws IOException {
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void deleteDocuments(Query q) throws IOException {
        throw new NotImplementedException();
    }

    @Override
    public long updateDocument(Term term, BLInputDocument document) throws IOException {
        pendingAddDocuments.add((BLInputDocumentSolr) document);
        return -1;
    }

    @Override
    public int getNumberOfDocs() {
        return pendingAddDocuments.size();
    }

    public List<BLInputDocumentSolr> getPendingAddDocuments() {
        return pendingAddDocuments;
    }
}
