package nl.inl.blacklab.performance;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.BLTerms;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.forwardindex.AnnotForwardIndex;
import nl.inl.blacklab.forwardindex.FieldForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.DocTask;
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

    public static void main(String[] args) throws ErrorOpeningIndex, IOException {

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
                exportTerms(index, annotatedField);
            if (doLengths || doTokens)
                exportDocs(index, annotatedField, forwardIndex, doLengths, doTokens);
        }
    }

    private static void exportDocs(BlackLabIndex index, AnnotatedField annotatedField, ForwardIndex forwardIndex, boolean doLengths, boolean doTokens) {
        // Export tokens in each doc
        System.out.println("\nDOCS");
        AtomicInteger n = new AtomicInteger(0);
        index.forEachDocument(false, new DocTask() {

            @Override
            public void document(LeafReaderContext segment, int segmentDocId) {
                int docId = segment.docBase + segmentDocId;
                if (n.incrementAndGet() > MAX_DOCS)
                    return;
                Document luceneDoc = index.luceneDoc(docId);
                String inputFile = luceneDoc.get("fromInputFile");
                String lengthInField = doLengths ? ", lenfield=" + luceneDoc.get(annotatedField.tokenLengthField()) : "";
                System.out.println(docId + "  file=" + inputFile + lengthInField);
                for (Annotation annotation: annotatedField.annotations()) {
                    if (SKIP_ANNOTATIONS.contains(annotation.name()) || !annotation.hasForwardIndex())
                        continue;
                    String luceneField = annotation.forwardIndexSensitivity().luceneField();
                    LeafReaderContext lrc = index.getLeafReaderContext(docId);
                    AnnotForwardIndex fi = FieldForwardIndex.get(lrc, luceneField);
                    int docLength = (int) fi.docLength(docId - lrc.docBase);
                    String length = doLengths ? " len=" + docLength : "";
                    System.out.println("    " + annotation.name() + length);
                    if (doTokens) {
                        int[] doc = fi.retrieveParts(docId - lrc.docBase, new int[] { -1 }, new int[] { -1 }).get(0);
                        Terms terms = fi.terms();
                        for (int tokenId: doc) {
                            String token = terms.get(tokenId);
                            System.out.println("    " + token);
                        }
                    }
                }
            }
        });
    }

    private static void exportTerms(BlackLabIndex index, AnnotatedField annotatedField) throws IOException {
        // Export term indexes + term strings
        System.out.println("TERMS");
        for (Annotation annotation: annotatedField.annotations()) {
            if (SKIP_ANNOTATIONS.contains(annotation.name()) || !annotation.hasForwardIndex())
                continue;
            System.out.println("  " + annotation.name());
            Set<String> allTerms = new TreeSet<>();
            for (LeafReaderContext lrc: index.reader().leaves()) {
                String luceneField = annotatedField.mainAnnotation().forwardIndexSensitivity().luceneField();
                Terms r = BLTerms.forSegment(lrc, luceneField).reader();
                for (int i = 0; i < r.numberOfTerms(); i++) {
                    allTerms.add(r.get(i));
                }
            }

            int i = 0;
            for (String term: allTerms) {
                System.out.println(String.format("    %03d %s", i, term));
                i++;
            }
        }
    }

    private static void usage() {
        System.out.println("Supply an index directory and, optionally, an annotated field name and what to export");
    }
}
