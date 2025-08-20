package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidIndex;

public class TermsIntegratedRef {

    private static Map<Pair<Directory, String>, TermsIntegratedRef> instances = new HashMap<>();

    public static synchronized void remove(Directory directory) {
        // Remove all instances for this directory
        instances.entrySet().removeIf(entry -> entry.getKey().getLeft().equals(directory));
    }

    public static synchronized TermsIntegratedRef get(Directory directory, String fieldName) {
        Pair<Directory, String> key = Pair.of(directory, fieldName);
        return instances.get(key);
    }

    public static synchronized TermsIntegratedRef get(IndexReader reader, String fieldName) {
        Directory directory = ((DirectoryReader) reader).directory();
        Pair<Directory, String> key = Pair.of(directory, fieldName);
        TermsIntegratedRef instance = instances.get(key);
        if (instance == null) {
            try {
                instance = new TermsIntegratedRef(reader, fieldName);
                instances.put(key, instance);
            } catch (IOException e) {
                throw new ErrorOpeningIndex("Error while initializing terms for " + fieldName, e);
            }
        }
        return instance;
    }

    private final IndexReader reader;

    private final String fieldName;

    private TermsIntegrated terms;

    public TermsIntegratedRef(IndexReader reader, String fieldName) throws IOException {
        this.reader = reader;
        this.fieldName = fieldName;
        this.terms = null;
    }

    public synchronized TermsIntegrated get() {
        if (terms == null) {
            try {
                terms = new TermsIntegrated(fieldName);
                terms.initialize(reader);
            } catch (InterruptedException e) {
                throw new InvalidIndex(e);
            }
        }
        return terms;
    }
}
