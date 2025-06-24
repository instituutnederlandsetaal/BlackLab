package nl.inl.blacklab.tools.frequency;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.fory.Fory;
import org.apache.fory.config.Language;
import org.apache.fory.io.ForyInputStream;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;

import de.siegmar.fastcsv.writer.CsvWriter;
import de.siegmar.fastcsv.writer.QuoteStrategies;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.resultproperty.HitGroupPropertyIdentity;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.searches.SearchCacheDummy;
import nl.inl.blacklab.searches.SearchHitGroups;
import nl.inl.util.LuceneUtil;
import nl.inl.util.Timer;

/**
 * Determine frequency lists over annotation(s) and
 * metadata field(s) for the entire index.
 */
public class FrequencyTool {

    static void exit(String msg) {
        System.out.println(msg);
        System.exit(1);
    }

    static void exitUsage(String msg) {
        if (!StringUtils.isEmpty(msg)) {
            System.out.println(msg + "\n");
        }
        exit("""
                Calculate term frequencies over annotation(s) and metadata field(s).
                
                Usage:
                
                  FrequencyTool [--gzip] INDEX_DIR CONFIG_FILE [OUTPUT_DIR]
                
                  --gzip       write directly to .gz file
                  --no-merge   don't merge chunk files, write separate tsvs instead
                  INDEX_DIR    index to generate frequency lists for
                  CONFIG_FILE  YAML file specifying what frequency lists to generate. See README.md.
                  OUTPUT_DIR   where to write TSV output files (defaults to current dir)
                
                """);
    }

    public static void main(String[] args) throws ErrorOpeningIndex {

        BlackLab.setConfigFromFile(); // read blacklab.yaml if exists and set config from that

        // Check for options
        int numOpts = 0;
        FreqListOutput.Type outputType = FreqListOutput.Type.TSV;
        for (String arg: args) {
            if (arg.startsWith("--")) {
                numOpts++;
                switch (arg) {
                case "--gzip":
                    outputType = FreqListOutput.Type.TSV_GZIP;
                    break;
                case "--no-merge":
                    outputType = FreqListOutput.Type.UNMERGED_TSV_GZ;
                    break;
                case "--help":
                    exitUsage("");
                    break;
                }
            } else
                break;
        }

        // Process arguments
        int numArgs = args.length - numOpts;
        if (numArgs < 2 || numArgs > 3) {
            exitUsage("Incorrect number of arguments.");
        }

        // Open index
        File indexDir = new File(args[numOpts]);
        if (!indexDir.isDirectory() || !indexDir.canRead()) {
            exit("Can't read or not a directory " + indexDir);
        }
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            // Read config
            File configFile = new File(args[numOpts + 1]);
            if (!configFile.canRead()) {
                exit("Can't read config file " + configFile);
            }
            Config config = Config.fromFile(configFile);
            System.out.println("CONFIGURATION:\n" + config.show());

            // Output dir
            File outputDir = new File(System.getProperty("user.dir")); // current dir
            if (numArgs > 2) {
                outputDir = new File(args[numOpts + 2]);
            }
            if (!outputDir.isDirectory() || !outputDir.canWrite()) {
                exit("Not a directory or cannot write to output dir " + outputDir);
            }

            Timer t = new Timer();

            // Generate the frequency lists
            makeFrequencyLists(index, config, outputDir, outputType);

            System.out.println("TOTAL TIME: " + t.elapsedDescription(true));
        }
    }

    private static void makeFrequencyLists(BlackLabIndex index, Config config, File outputDir, FreqListOutput.Type outputType) {
        AnnotatedField annotatedField = index.annotatedField(config.getAnnotatedField());
        config.check(index);
        index.setCache(new SearchCacheDummy()); // don't cache results
        for (ConfigFreqList freqList: config.getFrequencyLists()) {
            Timer t = new Timer();
            makeFrequencyList(index, annotatedField, freqList, outputDir, outputType, config);
            System.out.println("  Time: " + t.elapsedDescription());
        }
    }

    private static void makeFrequencyList(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList,
                                          File outputDir, FreqListOutput.Type outputType, Config config) {
        String reportName = freqList.getReportName();

        List<String> extraInfo = new ArrayList<>();
        if (config.getRepetitions() > 1)
            extraInfo.add(config.getRepetitions() + " repetitions");
        if (config.isUseRegularSearch())
            extraInfo.add("regular search");
        String strExtraInfo = extraInfo.isEmpty() ? "" : " (" + StringUtils.join(extraInfo, ", ") + ")";
        System.out.println("Generate frequency list" + strExtraInfo + ": " + reportName);

        if (config.isUseRegularSearch()) {
            // Skip optimizations (debug)
            makeFrequencyListUnoptimized(index, annotatedField, freqList, outputDir, outputType, config);
            return;
        }

        // Use specifically optimized CalcTokenFrequencies
        List<String> annotationNames = freqList.getAnnotations();
        Terms[] terms = annotationNames.stream()
                .map(name -> index.annotationForwardIndex(annotatedField.annotation(name)).terms())
                .toArray(Terms[]::new);
        List<Annotation> annotations = annotationNames.stream().map(annotatedField::annotation).collect(Collectors.toList());
        List<String> metadataFields = freqList.getMetadataFields();
        final List<Integer> docIds = getDocIds(index, freqList);

        // Create tmp dir for the chunk files
        File tmpDir = new File(outputDir, "tmp");
        if (!tmpDir.exists() && !tmpDir.mkdir())
            throw new RuntimeException("Could not create tmp dir: " + tmpDir);

        // Process the documents in parallel runs. After each run, check the size of the grouping,
        // and write it as a sorted chunk file if it exceeds the configured size.
        // At the end we will merge all the chunks to get the final result.
        List<File> chunkFiles = new ArrayList<>();
        final int docsToProcessInParallel = config.getDocsToProcessInParallel();
        int chunkNumber = 0;

        // This is where we store our groups while we're computing/gathering them.
        // Maps from group Id to number of hits and number of docs
        // ConcurrentMap because we're counting in parallel.
        ConcurrentMap<GroupIdHash, OccurrenceCounts> occurrences = null;

        for (int rep = 0; rep < config.getRepetitions(); rep++) { // FOR DEBUGGING

            for (int i = 0; i < docIds.size(); i += docsToProcessInParallel) {
                int runEnd = Math.min(i + docsToProcessInParallel, docIds.size());
                List<Integer> docIdsInChunk = docIds.subList(i, runEnd);

                // Make sure we have a map
                if (occurrences == null) {
                    // NOTE: we looked at ConcurrentSkipListMap which keeps entries in sorted order,
                    //       but it was faster to use a HashMap and sort it afterwards.
                    occurrences = new ConcurrentHashMap<>();
                }

                // Process current run of documents and add to grouping
                CalcTokenFrequencies.get(index, annotations, metadataFields, docIdsInChunk, occurrences, freqList.getNgramSize());

                System.out.println("  Processed docs " + i + "-" + runEnd + ", " + occurrences.size() + " entries");

                // If the grouping has gotten too large, write it to file so we don't run out of memory.
                boolean groupingTooLarge = occurrences.size() > config.getGroupsPerChunk();
                boolean isFinalRun = rep == config.getRepetitions() - 1 && runEnd >= docIds.size();
                if (groupingTooLarge || isFinalRun) {

                    if (isFinalRun && chunkNumber == 0) {
                        // There's only one chunk. We can skip writing intermediate file and write result directly.
                        FreqListOutput.TSV.write(index, annotatedField, reportName, annotationNames, occurrences,
                                outputDir, outputType == FreqListOutput.Type.TSV_GZIP);
                        occurrences = null;
                    } else if (groupingTooLarge || isFinalRun) {
                        // Sort our map now.
                        SortedMap<GroupIdHash, OccurrenceCounts> sorted = new TreeMap<>(occurrences);

                        // Write next chunk file.
                        chunkNumber++;
                        String chunkName = reportName + chunkNumber;
                        boolean tsv = outputType == FreqListOutput.Type.UNMERGED_TSV_GZ;
                        File chunkFile = new File(tmpDir, chunkName + (tsv ? ".tsv.gz" : ".chunk"));
                        System.out.println("  Writing " + chunkFile);
                        if (!tsv) {
                            // Write chunk files, to be merged at the end
                            writeChunkFile(chunkFile, sorted, config.isCompressTempFiles());
                            chunkFiles.add(chunkFile);
                        } else {
                            // Write separate TSV file per chunk; don't merge at the end
                            writeTsvFile(chunkFile, sorted, terms);
                        }
                        occurrences = null; // free memory, allocate new on next iteration
                    }
                }
            }
        }

        // Did we write intermediate chunk files that have to be merged?
        if (!chunkFiles.isEmpty()) {
            // Yes, merge all the chunk files. Because they are sorted, this will consume very little memory,
            // even if the final output file is huge.
            MatchSensitivity[] sensitivity = new MatchSensitivity[terms.length];
            Arrays.fill(sensitivity, MatchSensitivity.INSENSITIVE);
            mergeChunkFiles(chunkFiles, outputDir, reportName, outputType == FreqListOutput.Type.TSV_GZIP,
                    terms, sensitivity, config.isCompressTempFiles());

            // Remove chunk files
            for (File chunkFile: chunkFiles) {
                if (!chunkFile.delete())
                    System.err.println("Could not delete: " + chunkFile);
            }
        }
        if (!tmpDir.delete())
            System.err.println("Could not delete: " + tmpDir);
    }

    private static List<Integer> getDocIds(BlackLabIndex index, ConfigFreqList freqList) {
        final List<Integer> docIds = new ArrayList<>();

        String filter = freqList.getFilter();
        if (filter != null) {
            try {
                Query q = LuceneUtil.parseLuceneQuery(index, filter, index.analyzer(), "");
                index.queryDocuments(q).forEach(d -> docIds.add(d.docId()));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        } else {
            // No filter: include all documents.
            index.forEachDocument((__, id) -> docIds.add(id));
        }
        return docIds;
    }

    private static void writeChunkFile(File chunkFile, Map<GroupIdHash, OccurrenceCounts> occurrences, boolean compress) {
        try (OutputStream os = prepareStream(chunkFile, compress)) {
            // Write keys and values in sorted order, so we can merge later
            os.write(fory.serialize(occurrences.size())); // start with number of groups
            for (Map.Entry<GroupIdHash, OccurrenceCounts> entry: occurrences.entrySet()) {
                GroupIdHash key = entry.getKey();
                OccurrenceCounts value = entry.getValue();
                os.write(fory.serialize(key));
                os.write(fory.serialize(value));
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private static void writeTsvFile(File chunkFile, Map<GroupIdHash, OccurrenceCounts> occurrences, Terms[] terms) {
        boolean gzip = true;
        try (CsvWriter csv = prepareCSVPrinter(chunkFile, gzip)) {
            for (Map.Entry<GroupIdHash, OccurrenceCounts> entry: occurrences.entrySet()) {
                GroupIdHash key = entry.getKey();
                OccurrenceCounts value = entry.getValue();
                FreqListOutputTsv.writeGroupRecord(null, terms, csv, key, value.hits);
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    // Merge the sorted subgroupings that were written to disk, writing the resulting TSV as we go.
    // This takes very little memory even if the final output file is huge.
    private static void mergeChunkFiles(List<File> chunkFiles, File outputDir, String reportName, boolean gzip,
            Terms[] terms, MatchSensitivity[] sensitivity, boolean chunksCompressed) {
        File outputFile = new File(outputDir, reportName + ".tsv" + (gzip ? ".gz" : ""));
        System.out.println("  Merging " + chunkFiles.size() + " chunk files to produce " + outputFile);
        try (CsvWriter csv = prepareCSVPrinter(outputFile, gzip)) {
            int n = chunkFiles.size();
            InputStream[] inputStreams = new InputStream[n];
            InputStream[] gzipInputStreams = new InputStream[n];
            ForyInputStream[] chunks = new ForyInputStream[n];
            int[] numGroups = new int[n]; // groups per chunk file

            // These hold the index, key and value for the current group from every chunk file
            int[] index = new int[n];
            GroupIdHash[] key = new GroupIdHash[n];
            OccurrenceCounts[] value = new OccurrenceCounts[n];

            try {
                int chunksExhausted = 0;
                for (int i = 0; i < n; i++) {
                    File chunkFile = chunkFiles.get(i);
                    InputStream fis = new FileInputStream(chunkFile);
                    inputStreams[i] = fis;
                    InputStream gis = chunksCompressed ? new LZ4FrameInputStream(fis) : fis;
                    gzipInputStreams[i] = gis;
                    ForyInputStream ois = new ForyInputStream(gis);
                    numGroups[i] = (int) fory.deserialize(ois);
                    chunks[i] = ois;
                    // Initialize index, key and value with first group from each file
                    index[i] = 0;
                    key[i] = numGroups[i] > 0 ? (GroupIdHash) fory.deserialize(ois) : null;
                    value[i] = numGroups[i] > 0 ? (OccurrenceCounts) fory.deserialize(ois) : null;
                    if (numGroups[i] == 0)
                        chunksExhausted++;
                }

                // Now, keep merging the "lowest" keys together and advance them,
                // until we run out of groups.
                while (chunksExhausted < n) {
                    // Find lowest key value; we will merge that group next
                    GroupIdHash nextGroupToMerge = null;
                    for (int j = 0; j < n; j++) {
                        if (nextGroupToMerge == null || key[j] != null && key[j].compareTo(nextGroupToMerge) < 0)
                            nextGroupToMerge = key[j];
                    }

                    // Merge all groups with the lowest value,
                    // and advance those chunk files to the next group
                    int hits = 0, docs = 0;
                    for (int j = 0; j < n; j++) {
                        if (key[j] != null && key[j].equals(nextGroupToMerge)) {
                            // Add to merged counts
                            hits += value[j].hits;
                            docs += value[j].docs;
                            // Advance to next group in this chunk
                            index[j]++;
                            boolean noMoreGroupsInChunk = index[j] >= numGroups[j];
                            key[j] = noMoreGroupsInChunk ? null : (GroupIdHash) fory.deserialize(chunks[j]);
                            value[j] = noMoreGroupsInChunk ? null : (OccurrenceCounts) fory.deserialize(chunks[j]);
                            if (noMoreGroupsInChunk)
                                chunksExhausted++;
                        }
                    }

                    // Finally, write the merged group to the output file.
                    if (nextGroupToMerge != null)
                        FreqListOutputTsv.writeGroupRecord(sensitivity, terms, csv, nextGroupToMerge, hits);
                }

            } finally {
                for (ForyInputStream chunk: chunks)
                    chunk.close();
                if (chunksCompressed) {
                    for (InputStream gis : gzipInputStreams)
                        gis.close();
                }
                for (InputStream fis: inputStreams)
                    fis.close();
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    // Non memory-optimized version
    private static void makeFrequencyListUnoptimized(BlackLabIndex index, AnnotatedField annotatedField,
            ConfigFreqList freqList, File outputDir, FreqListOutput.Type outputType, Config config) {

        // Create our search
        try {
            // Execute search and write output file
            SearchHitGroups search = getSearch(index, annotatedField, freqList);
            HitGroups result = null;
            for (int i = 0; i < Math.max(1, config.getRepetitions()); i++) {
                result = search.execute();
            }
            FreqListOutput.TSV.write(index, annotatedField, freqList, result, outputDir,
                    outputType == FreqListOutput.Type.TSV_GZIP);
        } catch (InvalidQuery e) {
            throw new BlackLabRuntimeException("Error creating freqList " + freqList.getReportName(), e);
        }
    }

    private static SearchHitGroups getSearch(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList) {
        QueryInfo queryInfo = QueryInfo.create(index);
        BLSpanQuery anyToken = new SpanQueryAnyToken(queryInfo, 1, 1, annotatedField.name());
        HitProperty groupBy = getGroupBy(index, annotatedField, freqList);
        return index.search()
                .find(anyToken)
                .groupStats(groupBy, 0)
                .sort(new HitGroupPropertyIdentity());
    }

    private static HitProperty getGroupBy(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList) {
        List<HitProperty> groupProps = new ArrayList<>();
        // Add annotations to group by
        for (String name: freqList.getAnnotations()) {
            Annotation annotation = annotatedField.annotation(name);
            groupProps.add(new HitPropertyHitText(index, annotation));
        }
        // Add metadata fields to group by
        for (String name: freqList.getMetadataFields()) {
            groupProps.add(new HitPropertyDocumentStoredField(index, name));
        }
        return new HitPropertyMultiple(groupProps.toArray(new HitProperty[0]));
    }

    private static OutputStream prepareStream(File file, boolean gzip) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        return gzip ? new LZ4FrameOutputStream(fos) : fos;
    }

    public static CsvWriter prepareCSVPrinter(File file, boolean gzip) throws IOException {
        OutputStream stream = prepareStream(file, gzip);
        Writer w = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        return CsvWriter.builder().fieldSeparator('\t').quoteStrategy(QuoteStrategies.EMPTY).build(w);
    }

    private static final Fory fory = getFory();

    private static Fory getFory() {
        Fory fory = Fory.builder().withLanguage(Language.JAVA).requireClassRegistration(true).withAsyncCompilation(true)
                .withStringCompressed(true).build();
        fory.register(GroupIdHash.class);
        fory.register(OccurrenceCounts.class);
        return fory;
    }
}
