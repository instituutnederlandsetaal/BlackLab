package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import de.siegmar.fastcsv.writer.CsvWriter;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.blacklab.tools.frequency.data.GroupIdHash;
import nl.inl.blacklab.tools.frequency.data.OccurrenceCounts;
import nl.inl.util.Timer;

/**
 * Writes frequency results to a TSV file.
 */
public final class TsvWriter extends FreqListWriter {

    public TsvWriter(final BuilderConfig bCfg, final FreqListConfig fCfg, final AnnotationInfo aInfo) {
        super(bCfg, fCfg, aInfo);
    }

    /**
     * Write in a database suitable format using IDs instead of strings.
     */
    private static String writeIdRecord(final int ngramSize, final int[] tokenIds, final int tokenArrIndex) {
        int[] tokenList = new int[ngramSize];
        System.arraycopy(tokenIds, tokenArrIndex, tokenList, 0, ngramSize);

        if (ngramSize == 1) {
            return Integer.toString(tokenList[0]);
        } else {
            return formatNGram(tokenList);
        }
    }

    /**
     * Format n-gram tokens as a array string. E.g. "{1,2,3}"
     *
     * @param tokens the token list
     * @return formatted n-gram
     */
    private static String formatNGram(final int[] tokens) {
        StringBuilder sb = new StringBuilder("{");
        for (int token: tokens) {
            sb.append(token);
            sb.append(",");
        }
        // replace last comma with closing brace
        sb.setCharAt(sb.length() - 1, '}');
        return sb.toString();
    }

    private static String writeStringRecord(final int ngramSize, final int[] tokenIds, final int tokenArrIndex,
            final Terms termIndex) {
        // map token int ids to their string values
        String[] tokenList = new String[ngramSize];
        for (int j = 0; j < ngramSize; j++) {
            tokenList[j] = MatchSensitivity.INSENSITIVE.desensitize(termIndex.get(tokenIds[tokenArrIndex + j]));
        }
        // join with a space
        return String.join(" ", tokenList);
    }

    private static void addMetadataToRecord(final GroupIdHash groupId, final List<String> record) {
        String[] metadataValues = groupId.getMetadataValues();
        if (metadataValues != null)
            Collections.addAll(record, metadataValues);
    }

    /**
     * Write HitGroups result.
     *
     * @param result grouping result
     */
    public void write(final HitGroups result) {
        final var file = getFile();
        try (final var csv = getCsvWriter(file)) {
            for (final HitGroup group: result) {
                final List<String> record = new ArrayList<>();
                final var values = group.identity().valuesList();
                for (final var value: values)
                    record.add(value.toString());
                record.add(Long.toString(group.size()));
                csv.writeRecord(record);
            }
        } catch (IOException e) {
            throw reportIOException(e);
        }
    }

    /**
     * Write Map result.
     *
     * @param occurrences grouping result
     */
    public void write(final Map<GroupIdHash, OccurrenceCounts> occurrences) {
        final var t = new Timer();
        final var file = getFile();
        try (final var csv = getCsvWriter(file)) {
            for (final var entry: occurrences.entrySet()) {
                writeGroupRecord(csv, entry.getKey(), entry.getValue().hits);
            }
        } catch (IOException e) {
            throw reportIOException(e);
        }
        System.out.println("  Wrote " + file + " in " + t.elapsedDescription(true));
    }

    void writeGroupRecord(final CsvWriter csv, final GroupIdHash groupId, final int hits) throws IOException {
        final List<String> record = new ArrayList<>();
        // - annotation values
        addAnnotationsToRecord(groupId, record);
        // - metadata values
        addMetadataToRecord(groupId, record);
        // - group size (hits/docs)
        record.add(Long.toString(hits));
        csv.writeRecord(record);
    }

    private void addAnnotationsToRecord(final GroupIdHash groupId, final List<String> record) {
        int[] tokenIds = groupId.getTokenIds();
        // for each annotation construct a string for the ngram
        int ngramSize = groupId.getNgramSize();
        for (int i = 0, tokenArrIndex = 0; tokenArrIndex < tokenIds.length; i++, tokenArrIndex += ngramSize) {
            String token;
            if (bCfg.isDatabaseFormat()) {
                token = writeIdRecord(ngramSize, tokenIds, tokenArrIndex);
            } else {
                // get term index for the annotation
                Terms termIndex = aInfo.getTerms().get(i); // contains id to string mapping
                token = writeStringRecord(ngramSize, tokenIds, tokenArrIndex, termIndex);
            }
            record.add(token);
        }
    }

    private File getFile() {
        final String fileName = fCfg.getReportName() + getExt();
        return new File(bCfg.getOutputDir(), fileName);
    }
}
