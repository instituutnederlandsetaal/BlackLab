package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import de.siegmar.fastcsv.writer.CsvWriter;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.data.GroupIdHash;
import nl.inl.blacklab.tools.frequency.data.OccurrenceCounts;

/**
 * Writes frequency results to a TSV file.
 */
public class TsvWriter extends FreqListWriter {

    static void writeGroupRecord(MatchSensitivity[] sensitivity, Terms[] terms, CsvWriter csv, GroupIdHash groupId,
            int hits) throws IOException {
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
        csv.writeRecord(record);
    }

    /**
     * Write HitGroups result.
     *
     * @param fCfg   configuration
     * @param result grouping result
     * @param config global configuration
     */
    public static void write(FreqListConfig fCfg, HitGroups result, BuilderConfig config) {
        File outputFile = new File(config.getOutputDir(),
                fCfg.getReportName() + ".tsv" + (config.isCompressed() ? ".lz4" : ""));
        try (CsvWriter csv = getCsvWriter(outputFile, config.isCompressed())) {
            for (HitGroup group: result) {
                List<String> record = new ArrayList<>();
                PropertyValue identity = group.identity();
                for (PropertyValue value: identity.valuesList())
                    record.add(value.toString());
                record.add(Long.toString(group.size()));
                csv.writeRecord(record);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error writing output for " + fCfg.getReportName(), e);
        }
    }

    /**
     * Write Map result.
     *
     * @param index           index
     * @param annotatedField  annotated field
     * @param annotationNames annotations to group on
     * @param occurrences     grouping result
     * @param config          global configuration
     */
    public static void write(BlackLabIndex index, AnnotatedField annotatedField,
            List<String> annotationNames, Map<GroupIdHash, OccurrenceCounts> occurrences,
            BuilderConfig config, FreqListConfig fCfg) {
        File outputFile = new File(config.getOutputDir(),
                fCfg.getReportName() + ".tsv" + (config.isCompressed() ? ".lz4" : ""));
        System.out.println("  Writing " + outputFile);
        try (CsvWriter csv = getCsvWriter(outputFile, config.isCompressed())) {
            Terms[] terms = annotationNames.stream()
                    .map(name -> index.annotationForwardIndex(annotatedField.annotation(name)).terms())
                    .toArray(Terms[]::new);
            MatchSensitivity[] sensitivity = new MatchSensitivity[terms.length];
            Arrays.fill(sensitivity, MatchSensitivity.INSENSITIVE);
            for (Map.Entry<GroupIdHash,
                    OccurrenceCounts> e: occurrences.entrySet()) {
                OccurrenceCounts occ = e.getValue();
                writeGroupRecord(sensitivity, terms, csv, e.getKey(), occ.hits);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error writing output for " + fCfg.getReportName(), e);
        }
    }
}
