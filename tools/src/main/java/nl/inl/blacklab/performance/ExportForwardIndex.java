package nl.inl.blacklab.performance;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.util.LogUtil;

/**
 * Executes a batch of fetch operations on a forward index.
 */
public class ExportForwardIndex {

    // Annotations to skip
    private static final List<String> SKIP_ANNOTATIONS = List.of(
            "pos",
            AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME,
            AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME
    );

    private static final int MAX_DOCS = 30;

    private ExportForwardIndex() {
    }

    public static void main(String[] args) throws ErrorOpeningIndex {

        LogUtil.setupBasicLoggingConfig(); // suppress log4j warning

        int fileArgNumber = 0;
        File indexDir = null;
        String annotatedFieldName = "contents";
        String whatToExport = "all";
        for (String s: args) {
            String arg = s.trim();
            if (arg.charAt(0) == '-') {
                System.err.println("Illegal option: " + arg);
                usage();
                return;
            }
            switch (fileArgNumber) {
            case 0:
                indexDir = new File(arg);
                if (!indexDir.exists() || !indexDir.isDirectory()) {
                    System.err.println("Index directory not found: " + arg);
                    usage();
                    return;
                }
                break;
            case 1:
                annotatedFieldName = arg;
                break;
            case 2:
                whatToExport = arg;
                break;
            default:
                System.err.println("Too many file arguments (supply index dir)");
                usage();
                return;
            }
            fileArgNumber++;
        }
        if (fileArgNumber < 1) {
            System.err.println("Too few file arguments (supply index dir)");
            usage();
            return;
        }

        boolean doTerms = whatToExport.equals("terms") || whatToExport.equals("all");
        boolean doTokens = whatToExport.equals("tokens") || whatToExport.equals("all");
        boolean doLengths = whatToExport.equals("lengths") || whatToExport.equals("all");

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            AnnotatedField annotatedField = index.annotatedField(annotatedFieldName);
            ForwardIndex forwardIndex = index.forwardIndex(annotatedField);

            if (doTerms)
                exportTerms(annotatedField, forwardIndex);
            if (doLengths || doTokens)
                exportDocs(index, annotatedField, forwardIndex, doLengths, doTokens);
        }
    }

    private static void exportDocs(BlackLabIndex index, AnnotatedField annotatedField, ForwardIndex forwardIndex, boolean doLengths, boolean doTokens) {
        // Export tokens in each doc
        System.out.println("\nDOCS");
        AtomicInteger n = new AtomicInteger(0);
        index.forEachDocument((__, docId) -> {
            if (n.incrementAndGet() > MAX_DOCS)
                return;
            Document luceneDoc = index.luceneDoc(docId);
            String inputFile = luceneDoc.get("fromInputFile");
            String lengthInField = doLengths ? ", lenfield=" + luceneDoc.get(annotatedField.tokenLengthField()) : "";
            System.out.println(docId + "  file=" + inputFile + lengthInField);
            for (Annotation annotation: annotatedField.annotations()) {
                if (SKIP_ANNOTATIONS.contains(annotation.name()) || !annotation.hasForwardIndex())
                    continue;
                AnnotationForwardIndex afi = forwardIndex.get(annotation);
                String length = doLengths ? " len=" + afi.docLength(docId) : "";
                System.out.println("    " + annotation.name() + length);
                if (doTokens) {
                    int[] doc = afi.getDocument(docId);
                    for (int tokenId: doc) {
                        String token = afi.terms().get(tokenId);
                        System.out.println("    " + token);
                    }
                }
            }
        });
    }

    private static void exportTerms(AnnotatedField annotatedField, ForwardIndex forwardIndex) {
        // Export term indexes + term strings
        System.out.println("TERMS");
        for (Annotation annotation: annotatedField.annotations()) {
            if (SKIP_ANNOTATIONS.contains(annotation.name()) || !annotation.hasForwardIndex())
                continue;
            System.out.println("  " + annotation.name());
            Terms terms = forwardIndex.terms(annotation);
            for (int i = 0; i < terms.numberOfTerms(); i++) {
                System.out.println(String.format("    %03d %s", i, terms.get(i)));
            }
        }
    }

    private static void usage() {
        System.out.println("Supply an index directory and, optionally, an annotated field name and what to export");
    }
}
