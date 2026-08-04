package nl.inl.blacklab.index;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
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

    /** Which field, if any, contains our persistent identifiers. Otherwise null.
     * Lazily initialized on first use; only access through getPidFieldName().
     */
    private String pidFieldName;

    /** Have we looked for the pid field name? If true and pidFieldName is null, there is no pid field, don't look again. */
    private boolean pidFieldNameInitialized = false;

    /** All persistent identifier field values in this index so far.
     * Lazily initialized on first use. Only access through addToPids().
     */
    private Set<String> usedPids = null;

    public BLIndexWriterProxyLucene(IndexWriter indexWriter, BlackLabIndexWriter index) {
        this.indexWriter = indexWriter;
        this.index = index;

    }

    private synchronized String getPidFieldName() {
        if (pidFieldName == null && !pidFieldNameInitialized) {
            MetadataField pidField = index.metadata().metadataFields().pidField();
            pidFieldName = pidField == null ? null : pidField.name();
            pidFieldNameInitialized = true;
        }
        return pidFieldName;
    }

    /** Get the PID for this document, if the index has a PID field.
     *
     * @param document the document
     * @return the PID term, or null if no PID field is configured
     */
    private Term getPidTerm(BLInputDocument document) {
        String pidFieldName = getPidFieldName();
        if (pidFieldName == null)
            throw new ErrorIndexingFile("Missing pid field name");
        String pid = document.get(pidFieldName);
        if (pid == null) {
            throw new ErrorIndexingFile("Document has no persistent identifier (pidField '" + pidFieldName +
                    "'). Document: " + document);
        }
        String desensitizedPid = StringUtil.desensitize(pid); // lowercase, remove accents
        return new Term(pidFieldName, desensitizedPid);
    }

    @Override
    public void addDocument(BLInputDocument document) throws IOException {
        // Do we have a persistent identifier?
        Document doc = luceneDoc(document);
        if (getPidFieldName() != null) {
            // We have a persistent identifier; ensure it only occurs once in the corpus.
            Term pidTerm = getPidTerm(document);
            BlackLabIndexWriter.IfDocumentExists ifDocumentExists = index.getIfDocumentExists();
            addOrUpdate(doc, pidTerm, ifDocumentExists);
        } else {
            // We don't have persistent identifiers. Just add the document.
            indexWriter.addDocument(doc);
        }
    }

    /**
     * Atomically add or update document (or skip/fail, depending on config).
     */
    private synchronized void addOrUpdate(Document doc, Term pidTerm,
            BlackLabIndexWriter.IfDocumentExists ifDocumentExists) throws IOException {
        if (!addToPids(pidTerm.text())) {
            // Already exists; handle according to configuration
            switch (ifDocumentExists) {
            case UPSERT -> indexWriter.updateDocument(pidTerm, doc);
            case SKIP -> { /* do nothing */ }
            case FAIL -> throw new ErrorIndexingFile("Document with pid '" + pidTerm.text() +
                    "' already exists in corpus; cannot add it again " +
                    "(ifDocumentExists setting set to 'fail'; set to 'replace' to upsert instead)");
            default -> throw new IllegalArgumentException();
            }
        } else {
            // Not in the index yet; add it now.
            indexWriter.addDocument(doc);
        }
    }

    /**
     * Add a pid to the set of used pids.
     *
     * @param pid the pid to add
     * @return true if it's a new pid, false if it was already present
     */
    private synchronized boolean addToPids(String pid) {
        if (usedPids == null) {
            usedPids = new ObjectOpenHashSet<>(getNumberOfDocs());
            String pidFieldName1 = getPidFieldName();
            if (pidFieldName1 != null) {
                try (IndexReader reader = DirectoryReader.open(indexWriter)) {
                    var fields = reader.storedFields();
                    var fieldsToVisit = Collections.singleton(pidFieldName1);

                    for (int i = 0; i < reader.maxDoc(); i++) {
                        var visitor = new DocumentStoredFieldVisitor(fieldsToVisit) {
                            private boolean found = false;

                            @Override
                            public Status needsField(FieldInfo fieldInfo) {
                                if (found)
                                    return Status.STOP;
                                if (fieldsToVisit.contains(fieldInfo.name)) {
                                    found = true;
                                    return Status.YES;
                                }
                                return Status.NO;
                            }
                        };
                        fields.document(i, visitor);
                        var doc = visitor.getDocument();

                        String pid1 = doc.get(pidFieldName1);
                        if (pid1 != null) {
                            usedPids.add(pid1);
                        }
                    }
                } catch (IOException e) {
                    throw new ErrorIndexingFile("Error gathering existing persistent identifiers from index", e);
                }
            }
        }
        return usedPids.add(pid);
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
