package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.DocTask;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.util.LogUtil;

/** Export the metadata of all documents from a BlackLab index. */
public class ExportMetadata implements AutoCloseable {

    private static String escapeTabs(String str) {
        return str.replace("\t", "\\t");
    }

    public static void main(String[] args) {
        LogUtil.setupBasicLoggingConfig(Level.DEBUG);

        if (args.length != 2) {
            System.out.println("Usage: ExportMetadata <indexDir> <exportFile>");
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

        File exportFile = new File(args[1]);

        try (ExportMetadata exportMetadata = new ExportMetadata(indexDir)) {
            exportMetadata.collectAndExport(exportFile);
        } catch (Exception e) {
            throw new InvalidIndex(e);
        }
    }

    final Set<String> fieldNames = new LinkedHashSet<>();

    BlackLabIndex index;

    public ExportMetadata(File indexDir) throws ErrorOpeningIndex {
        System.out.println("Open index " + indexDir + "...");
        index = BlackLab.open(indexDir);
        System.out.println("Done.");

        // Ensure pid field is first so we can easily sort by it
        MetadataField pidField = index.metadataFields().pidField();
        if (pidField != null)
            fieldNames.add(pidField.name()); // Ensure pid is first column
    }

    private void collectAndExport(File exportFile) throws IOException {
        File tmpFile = new File(exportFile.getAbsolutePath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmpFile);
                OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.TDF)) {
            System.out.println("Collecting metadata...");
            collect(csvPrinter);
            System.out.println("Exporting metadata...");
            System.out.println("Done exporting metadata.");
            System.out.flush();
        }
        try (FileOutputStream out = new FileOutputStream(exportFile);
                OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            // Write header
            writer.append(StringUtils.join(fieldNames, "\t") + "\r\n");
            // Copy data from tmp file
            try (java.io.FileInputStream in = new java.io.FileInputStream(tmpFile);
                    java.io.InputStreamReader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
                char[] buffer = new char[8192];
                int len;
                while ((len = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, len);
                }
            }
            if (!tmpFile.delete())
                throw new IOException("Failed to delete temporary file: " + tmpFile);
        }
    }

    /**
     * Export the corpus metadata.
     */
    private void collect(CSVPrinter csvPrinter) throws IOException {

        System.out.println("Getting IndexReader...");
        final IndexReader reader = index.reader();

        System.out.println("Calling forEachDocument()...");
        index.forEachDocument(true, new DocTask() {

            final AtomicInteger docsDone = new AtomicInteger(0);

            @Override
            public void document(LeafReaderContext segment, int segmentDocId) {
                int docId = segment.docBase + segmentDocId;
                Map<String, String> metadata = new HashMap<>();
                Document luceneDoc = index.luceneDoc(docId);
                for (IndexableField f: luceneDoc.getFields()) {
                    // If this is a regular metadata field, not a control field
                    if (f.name().equals("contents") || f.name().equals("metadata")) {
                        // HACK: skip common original document contents fields
                        continue;
                    }
                    if (!f.name().contains("#")) {
                        synchronized (fieldNames) {
                            fieldNames.add(f.name());
                        }
                        String value = f.stringValue();
                        if (value != null) {
                            if (value.length() > 255)
                                value = StringUtils.abbreviate(value, 255);
                            metadata.put(f.name(), value);
                        }
                        else if (f.numericValue() != null)
                            metadata.put(f.name(), f.numericValue().toString());
                    }
                }
                try {
                    synchronized (fieldNames) {
                        Stream<String> rec = fieldNames.stream().map(f -> metadata.getOrDefault(f, ""));
                        csvPrinter.printRecord(rec);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                int n = docsDone.incrementAndGet();
                if (n % 100 == 0) {
                    System.out.println(docsDone + " docs exported...");
                }
            }
        });
    }

    @Override
    public void close() {
        if (index != null)
            index.close();
    }
}
