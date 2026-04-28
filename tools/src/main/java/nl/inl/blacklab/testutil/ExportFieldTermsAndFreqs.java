package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.Level;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.LogUtil;

/** WIP Export the terms for a field in the index. */
public class ExportFieldTermsAndFreqs implements AutoCloseable {

    public static void main(String[] args) {
        LogUtil.setupBasicLoggingConfig(Level.DEBUG);

        if (args.length != 2) {
            System.out.println("Usage: ExportFieldTermsAndFreqs <indexDir> <luceneFieldName>");
            System.exit(1);
        }

        File indexDir = new File(args[0]);
        if (!indexDir.isDirectory() || !indexDir.canRead()) {
            System.out.println("Directory doesn't exist or is unreadable: " + indexDir);
            System.exit(1);
        }
        if (!BlackLabIndex.isIndex(indexDir)) {
            System.out.println("Not a BlackLab index: " + indexDir);
            System.exit(1);
        }

        String field = args[1];

        try (ExportFieldTermsAndFreqs exportMetadata = new ExportFieldTermsAndFreqs(indexDir)) {
            exportMetadata.collectAndExport(field);
        } catch (Exception e) {
            throw new InvalidIndex(e);
        }
    }

    BlackLabIndex index;

    public ExportFieldTermsAndFreqs(File indexDir) throws ErrorOpeningIndex {
        System.out.println("Open index " + indexDir + "...");
        index = BlackLab.open(indexDir);
        System.out.println("Done.");
    }

    private void collectAndExport(String luceneField) throws IOException {
        // Write final export file with header
        try (OutputStreamWriter writer = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.TDF)) {

            index.forEachDocument(lrc -> docId -> {
                try {
                    Terms terms = lrc.reader().termVectors().get(docId, luceneField);
                    TermsEnum termsEnum = terms.iterator();
                    //TODO
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            //csvPrinter.printRecord(rec);
        }
    }

    @Override
    public void close() {
        if (index != null)
            index.close();
    }
}
