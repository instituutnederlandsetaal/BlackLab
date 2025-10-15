package nl.inl.blacklab.index;

import java.io.Closeable;
import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.util.StringUtil;

/**
 * Simple proxy for Lucene IndexWriter.
 */
public class BLIndexWriterProxyLucene implements BLIndexWriterProxy, Closeable {

    private final BlackLabIndexWriter index;

    private final IndexWriter indexWriter;

    private String pidFieldName;

    public BLIndexWriterProxyLucene(IndexWriter indexWriter, BlackLabIndexWriter index) {
        this.indexWriter = indexWriter;
        this.index = index;

    }

    synchronized String getPidFieldName() {
        if (pidFieldName == null) {
            MetadataField pidField = index.metadata().metadataFields().pidField();
            pidFieldName = pidField == null ? null : pidField.name();
        }
        return pidFieldName;
    }

    @Override
    public void addDocument(BLInputDocument document) throws IOException {
        String pidFieldName = getPidFieldName();
        if (pidFieldName != null) {
            // Index has a persistent identifier.
            String pid = document.get(pidFieldName);
            if (pid == null) {
                // pidField (persistent identifier) must be specified
                throw new ErrorIndexingFile("Document has no persistent identifier (pidField '" + pidFieldName +
                        "'). Document: " + document);
            }
            // See if a document with this pid already exists
            try (DirectoryReader reader = DirectoryReader.open(indexWriter)) {
                String desensitizedPid = StringUtil.desensitize(pid); // lowercase, remove accents
                Term pidTerm = new Term(pidFieldName, desensitizedPid);
                int n = reader.docFreq(pidTerm);
                if (n > 0) {
                    switch (index.getIfDocumentExists()) {
                    case UPSERT ->
                        // Document with this pid already exists; delete it first so we can add the new version
                            indexWriter.deleteDocuments(new TermQuery(pidTerm));
                    case SKIP -> {
                        // Document with this pid already exists; skip this one
                        return;
                    }
                    case FAIL ->
                        // Document with this pid already exists; fail
                            throw new ErrorIndexingFile(
                                    "Document with pid '" + pid + "' already exists in index; cannot add document: "
                                            + document);
                    }
                }
            }
        }
        indexWriter.addDocument(luceneDoc(document));
    }

    private Document luceneDoc(BLInputDocument document) {
        return ((BLInputDocumentLucene)document).getDocument();
    }

    @Override
    public void close() throws IOException {
        indexWriter.close();
    }

    @Override
    public void commit() throws IOException {
        indexWriter.commit();
    }

    @Override
    public void rollback() throws IOException {
        indexWriter.rollback();
    }

    @Override
    public boolean isOpen() {
        return indexWriter.isOpen();
    }

    public IndexWriter getWriter() {
        return indexWriter;
    }

    @Override
    public void deleteDocuments(Query q) throws IOException {
        indexWriter.deleteDocuments(q);
    }

    @Override
    public long updateDocument(Term term, BLInputDocument document) throws IOException {
        return indexWriter.updateDocument(term, luceneDoc(document));
    }

    @Override
    public int getNumberOfDocs() {
        return indexWriter.getDocStats().numDocs;
    }
}
