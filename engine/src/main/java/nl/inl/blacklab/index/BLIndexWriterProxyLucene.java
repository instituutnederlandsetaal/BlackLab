package nl.inl.blacklab.index;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.util.StringUtil;

import org.jspecify.annotations.Nullable;

/**
 * Simple proxy for Lucene IndexWriter.
 */
public class BLIndexWriterProxyLucene implements BLIndexWriterProxy, Closeable {
    private boolean pidsInitialized = false;
    private ObjectOpenHashSet<String> usedPids = null;

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

    /** Get the PID for this document, if the index has a PID field. */
    private @Nullable Term getPid(BLInputDocument document) {
        String pidFieldName = getPidFieldName();
        if (pidFieldName == null)
            return null;
        String pid = document.get(pidFieldName);
        if (pid == null) {
            throw new ErrorIndexingFile("Document has no persistent identifier (pidField '" + pidFieldName + "'). Document: " + document);
        }
        String desensitizedPid = StringUtil.desensitize(pid); // lowercase, remove accents
        return new Term(pidFieldName, desensitizedPid);
    }

    private enum Action { ADD, UPSERT, SKIP, FAIL }
    private synchronized Action getActionForPid(@Nullable Term pid) {
        if (pid == null) return Action.ADD;

        var usedPids = getUsedPids();
        if (!usedPids.add(pid.text())) { // is not a new entry in the set
            return switch(index.getIfDocumentExists()) {
                case UPSERT -> Action.UPSERT;
                case SKIP -> Action.SKIP;
                case FAIL -> Action.FAIL;
            };
        }
        return Action.ADD;
    }

    @Override
    public void addDocument(BLInputDocument document) throws IOException {
        Term pid = getPid(document);
        switch (getActionForPid(pid)) {
        case ADD -> { indexWriter.addDocument(luceneDoc(document)); }
        case UPSERT -> { indexWriter.updateDocument(pid, luceneDoc(document)); }
        case SKIP -> { /* do nothing */ }
        case FAIL ->  { throw new ErrorIndexingFile("Document with pid '" + pid + "' already exists in index; cannot add document: " + document); }
        };
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

    private Set<String> getUsedPids() {
        if (!pidsInitialized) {
            usedPids = new ObjectOpenHashSet<>();
            String pidFieldName = getPidFieldName();
            if (pidFieldName != null) {
                try (IndexReader reader = DirectoryReader.open(indexWriter)) {
                    var fields = reader.storedFields();
                    var fieldsToVisit = Collections.singleton(pidFieldName);

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

                        String pid = doc.get(pidFieldName);
                        if (pid != null) {
                            usedPids.add(pid);
                        }
                    }
                } catch (IOException e) {
                    throw new ErrorIndexingFile("Error gathering existing persistent identifiers from index", e);
                }
            }
        }

        pidsInitialized = true;
        return usedPids;
    }
}
