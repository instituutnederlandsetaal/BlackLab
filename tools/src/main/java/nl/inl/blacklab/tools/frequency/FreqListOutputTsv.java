package nl.inl.blacklab.tools.frequency;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.hitresults.HitGroup;
import nl.inl.blacklab.search.results.hitresults.HitGroups;

/**
 * Writes frequency results to a TSV file.
 */
class FreqListOutputTsv implements FreqListOutput {

    public static final CSVFormat TAB_SEPARATED_FORMAT =
            CSVFormat.TDF
                    .withEscape('\\')
                    .withQuoteMode(QuoteMode.NONE);

    static void writeGroupRecord(MatchSensitivity[] sensitivity, Terms[] terms, CSVPrinter csv, GroupIdHash groupId, int hits) throws IOException {
        List<String> record = new ArrayList<>();
        // - annotation values
        int[] tokenIds = groupId.getTokenIds();
        // for each annotation construct a string for the ngram
        int ngramSize = groupId.getNgramSize();
        for (int i = 0, tokenArrIndex = 0; tokenArrIndex < tokenIds.length; i++, tokenArrIndex += ngramSize) {
            // get respective sensitivity and term index for the annotation
            MatchSensitivity matchSensitivity = sensitivity == null ? MatchSensitivity.INSENSITIVE : sensitivity[i];
            Terms termIndex = terms[i]; // contains id to string mapping
            // map token int ids to their string values
            String[] tokenList = new String[ngramSize];
            for (int j = 0; j < ngramSize; j++) {
                tokenList[j] = matchSensitivity.desensitize(termIndex.get(tokenIds[tokenArrIndex + j]));
            }
            // join with a space
            String token = String.join(" ", tokenList);
            record.add(token);
        }
        // - metadata values
        String[] metadataValues = groupId.getMetadataValues();
        if (metadataValues != null)
            Collections.addAll(record, metadataValues);
        // - group size (hits/docs)
        record.add(Long.toString(hits));
        csv.printRecord(record);
    }

    /**
     * Write HitGroups result.
     *
     * @param index          index
     * @param annotatedField annotated field
     * @param freqList       configuration
     * @param result         grouping result
     * @param outputDir      where to write output file
     * @param gzip           whether to gzip output file
     */
    @Override
    public void write(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList,
                      HitGroups result, File outputDir, boolean gzip) {
        File outputFile = new File(outputDir, freqList.getReportName() + ".tsv" + (gzip ? ".gz" : ""));
        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            OutputStream stream = outputStream;
            if (gzip)
                stream = new GZIPOutputStream(stream);
            try (Writer out = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
                 CSVPrinter printer = new CSVPrinter(out, TAB_SEPARATED_FORMAT)) {
                for (HitGroup group : result) {
                    List<String> record = new ArrayList<>();
                    PropertyValue identity = group.identity();
                    for (PropertyValue value : identity.valuesList())
                        record.add(value.toString());
                    record.add(Long.toString(group.size()));
                    printer.printRecord(record);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error writing output for " + freqList.getReportName(), e);
        }
    }

    /**
     * Write Map result.
     *
     * @param index          index
     * @param annotatedField annotated field
     * @param reportName     report name (file name without extensions)
     * @param annotationNames annotations to group on
     * @param occurrences    grouping result
     * @param outputDir      where to write output file
     * @param gzip           whether to gzip output file
     */
    @Override
    public File write(BlackLabIndex index, AnnotatedField annotatedField, String reportName,
                      List<String> annotationNames, Map<GroupIdHash, OccurrenceCounts> occurrences,
                      File outputDir, boolean gzip) {
        File outputFile = new File(outputDir, reportName + ".tsv" + (gzip ? ".gz" : ""));
        System.out.println("  Writing " + outputFile);
        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            OutputStream stream = outputStream;
            if (gzip)
                stream = new GZIPOutputStream(stream);
            try (Writer out = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
                 CSVPrinter printer = new CSVPrinter(out, TAB_SEPARATED_FORMAT)) {
                Terms[] terms = annotationNames.stream()
                        .map(name -> index.forwardIndex(annotatedField.annotation(name)).terms())
                        .toArray(Terms[]::new);
                MatchSensitivity[] sensitivity = new MatchSensitivity[annotationNames.size()];
                Arrays.fill(sensitivity, MatchSensitivity.INSENSITIVE);
                for (Map.Entry<GroupIdHash,
                        OccurrenceCounts> e : occurrences.entrySet()) {
                    OccurrenceCounts occ = e.getValue();
                    writeGroupRecord(sensitivity, terms, printer, e.getKey(), occ.hits);
                }
            }
            return outputFile;
        } catch (IOException e) {
            throw new RuntimeException("Error writing output for " + reportName, e);
        }
    }
}
