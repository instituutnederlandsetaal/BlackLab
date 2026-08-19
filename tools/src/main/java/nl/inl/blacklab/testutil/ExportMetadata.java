package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ParallelDocTask;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.util.LogUtil;

/** Export the metadata of all documents from a BlackLab index. */
public class ExportMetadata implements AutoCloseable {

    // TODO: make SKIP_FIELDS and PID_FIELD configurable

    /**
     * Fields to skip exporting (e.g. large fields,
     * fromInputFile which varies depending on indexing setup).
     */
    private static final Set<String> SKIP_FIELDS = Set.of("contents", "metadata", "fromInputFile");

    /** pid first for easy sorting by document. */
    private static final String PID_FIELD = "pid";

    private static String escapeProblemChars(String str) {
        // Escape problematic characters in the export
        return str
            .replaceAll("\t", "\\\\t")
            .replaceAll("\n", "\\\\n");
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

        // Determine final export field order
        List<String> listFieldNames = new ArrayList<>();
        Set<String> left = new TreeSet<>(fieldNames); // TreeSet sorts fields alphabetically
        if (left.contains(PID_FIELD)) {
            // Make sure pid is exported first (for easy sorting)
            listFieldNames.add(PID_FIELD);
            left.remove(PID_FIELD);
        }
        listFieldNames.addAll(left); // add rest (in sorted order)

        // Write final export file with header
        try (FileOutputStream out = new FileOutputStream(exportFile);
                OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.TDF)) {
            // Write header
            csvPrinter.printRecord(listFieldNames);
            // Copy data from tmp tsv file

            try (FileInputStream in = new FileInputStream(tmpFile);
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
                CSVParser csvParser = new CSVParser(reader, CSVFormat.TDF)) {
                csvParser.forEach(record -> {
                    try {
                        Map<String, String> recordMap = new HashMap<>();
                        int i = 0;
                        for (String fieldName: fieldNames) {
                            recordMap.put(fieldName, record.get(i));
                            i++;
                            if (i >= record.size())
                                break;
                        }
                        Stream<String> rec = listFieldNames.stream().map(f -> recordMap.getOrDefault(f, ""));
                        csvPrinter.printRecord(rec);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            if (!tmpFile.delete())
                throw new IOException("Failed to delete temporary file: " + tmpFile);
        }
    }

    final int MAX_VALUE_LENGTH = 1000;

    /**
     * Export the corpus metadata.
     */
    private void collect(CSVPrinter csvPrinter) throws IOException {

        System.out.println("Calling forEachDocument()...");
        index.forEachDocument(new ParallelDocTask() {
            final AtomicInteger docsDone = new AtomicInteger(0);

            @Override
            public SegmentTask segmentDocTask(LeafReaderContext segment) {
                return segmentDocId -> {
                    int docId = segment.docBase + segmentDocId;
                    Map<String, String> metadata = new HashMap<>();
                    Document luceneDoc = index.luceneDoc(docId);
                    for (IndexableField f: luceneDoc.getFields()) {
                        // If this is a regular metadata field, not a control field or contents field
                        // (bit of a hack)
                        if (!f.name().contains("#") && !SKIP_FIELDS.contains(f.name())) {
                            synchronized (fieldNames) {
                                fieldNames.add(f.name());
                            }
                            String value = f.stringValue();
                            if (value != null) {
                                if (value.length() > MAX_VALUE_LENGTH)
                                    value = StringUtils.abbreviate(value, MAX_VALUE_LENGTH);
                                metadata.put(f.name(), escapeProblemChars(value));
                            } else if (f.numericValue() != null)
                                metadata.put(f.name(), escapeProblemChars(f.numericValue().toString()));
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
                };
            }
        });
    }

    @Override
    public void close() {
        if (index != null)
            index.close();
    }
}
