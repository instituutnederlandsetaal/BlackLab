package nl.inl.blacklab.tools.frequency;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidIndex;
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
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.searches.SearchCacheDummy;
import nl.inl.blacklab.searches.SearchHitGroups;
import nl.inl.util.LuceneUtil;
import nl.inl.util.Timer;

/**
 * Determine frequency lists over annotation(s) and
 * metadata field(s) for the entire index.
 */
public class FrequencyTool {

    private FrequencyTool() {
    }

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
        List<Annotation> annotations = annotationNames.stream().map(annotatedField::annotation).toList();
        List<String> metadataFields = freqList.getMetadataFields();
        final Map<LeafReaderContext, List<Integer>> docIds = getDocIds(index, freqList);

        // Create tmp dir for the chunk files
        File tmpDir = new File(outputDir, "tmp");
        if (!tmpDir.exists() && !tmpDir.mkdir())
            throw new RuntimeException("Could not create tmp dir: " + tmpDir);

        // Process the documents in parallel runs. After each run, check the size of the grouping,
        // and write it as a sorted chunk file if it exceeds the configured size.
        // At the end we will merge all the chunks to get the final result.
        List<File> chunkFiles = new ArrayList<>();
        final int docsToProcessInParallel = config.getDocsToProcessInParallel(); // TODO: limit number of threads?
        final AtomicInteger chunkNumber = new AtomicInteger(0);

        // This is where we store our groups while we're computing/gathering them.
        // Maps from group Id to number of hits and number of docs
        // ConcurrentMap because we're counting in parallel.
        final ConcurrentMap<GroupIdHash, OccurrenceCounts> globalOccurrences = new ConcurrentHashMap<>();

        for (int rep = 0; rep < config.getRepetitions(); rep++) { // FOR DEBUGGING

            docIds.entrySet().parallelStream().forEach(entry -> {
                LeafReaderContext lrc = entry.getKey();
                List<Integer> docIdsInSegment = entry.getValue();

                // Keep track of term occurrences in this segment; later we'll merge it with the global term frequencies
                Map<GroupIdHash, OccurrenceCounts> occsInSegment = CalcTokenFrequencies.get(index, lrc, annotations,
                        metadataFields, docIdsInSegment, freqList.getNgramSize());

                // Merge occurrences in this segment with global occurrences
                // (the group ids are converted to their string representation here)
                if (globalOccurrences instanceof ConcurrentSkipListMap) {
                    // NOTE: we cannot modify groupSize or occ here like we do in HitGroupsTokenFrequencies,
                    //       because we use ConcurrentSkipListMap, which may call the remapping function
                    //       multiple times if there's potential concurrency issues.
                    occsInSegment.forEach((groupId, occ) -> globalOccurrences.compute(groupId.toGlobalTermIds(lrc, annotations), (__, groupSize) -> {
                        if (groupSize == null)
                            return occ; // reusing occ here is okay because it doesn't change on subsequent calls
                        else
                            return new OccurrenceCounts(groupSize.hits + occ.hits, groupSize.docs + occ.docs);
                    }));
                } else {
                    // Not using ConcurrentSkipListMap but ConcurrentHashMap. It's okay to re-use occ,
                    // because our remapping function will only be called once.
                    occsInSegment.forEach((groupId, occ) -> globalOccurrences.compute(groupId.toGlobalTermIds(lrc, annotations), (__, groupSize) -> {
                        // NOTE: we cannot modify groupSize or occ here like we do in HitGroupsTokenFrequencies,
                        //       because we use ConcurrentSkipListMap, which may call the remapping function
                        //       multiple times if there's potential concurrency issues.
                        if (groupSize != null) {
                            // Group existed already
                            // Count hits and doc
                            occ.hits += groupSize.hits;
                            occ.docs += groupSize.docs;
                        }
                        return occ; // reusing occ here is okay because it doesn't change on subsequent calls
                    }));
                }

                //System.out.println("  Processed docs " + i + "-" + runEnd + ", " + globalOccurrences.size() + " entries");

                // If the grouping has gotten too large, write it to file so we don't run out of memory.
                if (globalOccurrences.size() > config.getGroupsPerChunk()) {
                    // Sort our map now.
                    SortedMap<GroupIdHash, OccurrenceCounts> sorted = new TreeMap<>(globalOccurrences);

                    // Write next chunk file.
                    chunkNumber.incrementAndGet();
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
                    globalOccurrences.clear();
                }
            });
        }

        // Did we write intermediate chunk files that have to be merged?
        if (!chunkFiles.isEmpty()) {
            // Yes, merge all the chunk files. Because they are sorted, this will consume very little memory,
            // even if the final output file is huge.
            MatchSensitivity[] sensitivity = new MatchSensitivity[annotationNames.size()];
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

    private static Map<LeafReaderContext, List<Integer>> getDocIds(BlackLabIndex index, ConfigFreqList freqList) {
        final Map<LeafReaderContext, List<Integer>> docIds = new HashMap<>();

        String filter = freqList.getFilter();
        if (filter != null) {
            try {
                Query filterQuery = LuceneUtil.parseLuceneQuery(index, filter, index.analyzer(), "");

                index.searcher().search(filterQuery == null ? index.getAllRealDocsQuery() : filterQuery, new SimpleCollector() {
                    private LeafReaderContext context;

                    @Override
                    protected void doSetNextReader(LeafReaderContext context) throws IOException {
                        this.context = context;
                        super.doSetNextReader(context);
                    }

                    @Override
                    public void collect(int segmentDocId) {
                        docIds.compute(context, (k, v) -> {
                            if (v == null)
                                v = new ArrayList<>();
                            v.add(segmentDocId);
                            return v;
                        });
                    }

                    @Override
                    public ScoreMode scoreMode() {
                        return ScoreMode.COMPLETE_NO_SCORES;
                    }
                });

            } catch (ParseException e) {
                throw new InvalidQuery(e);
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        } else {
            // No filter: include all documents.
            for (LeafReaderContext lrc: index.reader().leaves()) {
                Bits liveDocs = lrc.reader().getLiveDocs();
                List<Integer> docIdsInSegment = new ArrayList<>();
                for (int segmentDocId = 0; segmentDocId < lrc.reader().maxDoc(); segmentDocId++) {
                    if (liveDocs == null || liveDocs.get(segmentDocId)) {
                        docIdsInSegment.add(segmentDocId);
                    }
                }
                docIds.put(lrc, docIdsInSegment);
            }
        }
        return docIds;
    }

    private static void writeChunkFile(File chunkFile, Map<GroupIdHash, OccurrenceCounts> occurrences, boolean compress) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(chunkFile)) {
             OutputStream outputStream = compress ? new GZIPOutputStream(fileOutputStream) : fileOutputStream;
             try {
                 try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {

                     // Write keys and values in sorted order, so we can merge later
                     objectOutputStream.writeInt(occurrences.size()); // start with number of groups
                     occurrences.forEach((key, value) -> {
                         try {
                             objectOutputStream.writeObject(key);
                             objectOutputStream.writeObject(value);
                         } catch (IOException e) {
                             throw new RuntimeException();
                         }
                     });
                 }
             } finally {
                 if (compress)
                     outputStream.close();
             }

        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private static void writeTsvFile(File chunkFile, Map<GroupIdHash, OccurrenceCounts> occurrences, Terms[] terms) {
        boolean compress = true;
        try (FileOutputStream fileOutputStream = new FileOutputStream(chunkFile)) {
            OutputStream outputStream = compress ? new GZIPOutputStream(fileOutputStream) : fileOutputStream;
            try {
                try (Writer w = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                        CSVPrinter csv = new CSVPrinter(w, FreqListOutputTsv.TAB_SEPARATED_FORMAT)) {
                    for (Map.Entry<GroupIdHash, OccurrenceCounts> entry: occurrences.entrySet()) {
                        GroupIdHash key = entry.getKey();
                        OccurrenceCounts value = entry.getValue();
                        FreqListOutputTsv.writeGroupRecord(null, terms, csv, key, value.hits);
                    }
                }
            } finally {
                if (compress)
                    outputStream.close();
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
        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            OutputStream stream = outputStream;
            if (gzip)
                stream = new GZIPOutputStream(stream);
            try (Writer w = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
                 CSVPrinter csv = new CSVPrinter(w, FreqListOutputTsv.TAB_SEPARATED_FORMAT)) {
                int n = chunkFiles.size();
                InputStream[] inputStreams = new InputStream[n];
                InputStream[] gzipInputStreams = new InputStream[n];
                ObjectInputStream[] chunks = new ObjectInputStream[n];
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
                        InputStream gis = chunksCompressed ? new GZIPInputStream(fis) : fis;
                        gzipInputStreams[i] = gis;
                        ObjectInputStream ois = new ObjectInputStream(gis);
                        numGroups[i] = ois.readInt();
                        chunks[i] = ois;
                        // Initialize index, key and value with first group from each file
                        index[i] = 0;
                        key[i] = numGroups[i] > 0 ? (GroupIdHash) ois.readObject() : null;
                        value[i] = numGroups[i] > 0 ? (OccurrenceCounts) ois.readObject() : null;
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
                                key[j] = noMoreGroupsInChunk ? null : (GroupIdHash) chunks[j].readObject();
                                value[j] = noMoreGroupsInChunk ? null : (OccurrenceCounts) chunks[j].readObject();
                                if (noMoreGroupsInChunk)
                                    chunksExhausted++;
                            }
                        }

                        // Finally, write the merged group to the output file.
                        if (nextGroupToMerge != null)
                            FreqListOutputTsv.writeGroupRecord(sensitivity, terms, csv, nextGroupToMerge, hits);
                    }

                } catch (ClassNotFoundException e) {
                    throw new RuntimeException();
                } finally {
                    for (ObjectInputStream chunk: chunks)
                        chunk.close();
                    if (chunksCompressed) {
                        for (InputStream gis : gzipInputStreams)
                            gis.close();
                    }
                    for (InputStream fis: inputStreams)
                        fis.close();
                }
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
            throw new RuntimeException("Error creating freqList " + freqList.getReportName(), e);
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
}
