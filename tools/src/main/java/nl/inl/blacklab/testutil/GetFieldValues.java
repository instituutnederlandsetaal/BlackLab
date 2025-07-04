package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;

public class GetFieldValues {
    private GetFieldValues() {
    }

    public static void main(String[] args) throws ErrorOpeningIndex, IOException {

        if (args.length < 2) {
            System.err.println("Usage: GetFieldValues <indexDir> <fieldName1> <fieldName2> ...");
            return;
        }

        File indexDir = new File(args[0]);
        List<String> fieldNames = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            fieldNames.add(args[i]);
        }

        Map<String, Set<String>> fieldValues = new HashMap<>();
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            IndexReader r = index.reader();

            Set<String> fieldsToLoad = new HashSet<>();
            fieldsToLoad.addAll(fieldNames);
            DocumentStoredFieldVisitor fieldVisitor = new DocumentStoredFieldVisitor(fieldsToLoad);

            int numDocs = r.numDocs();
            for (int i = 1; i < numDocs; i++) {
                r.document(i, fieldVisitor);
                Document d = fieldVisitor.getDocument();
                for (String fieldName : fieldNames) {
                    String value = d.get(fieldName);
                    if (value != null) {
                        Set<String> uniq;
                        uniq = fieldValues.computeIfAbsent(fieldName, k -> new TreeSet<>());
                        // TreeSet auto-sorts
                        uniq.add(value);
                    }
                }
            }
        }

        for (Map.Entry<String, Set<String>> e : fieldValues.entrySet()) {
            System.out.println("\n### " + e.getKey() + ":");
            for (String term : e.getValue()) {
                System.out.println(term);
            }
        }
    }
}
