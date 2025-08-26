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
import nl.inl.blacklab.tools.frequency.data.GroupId;
import nl.inl.util.Timer;

/**
 * Writes frequency results to a TSV file.
 */
public final class TsvWriter extends FreqListWriter {

    public TsvWriter(final BuilderConfig bCfg, final FreqListConfig fCfg, final AnnotationInfo aInfo) {
        super(bCfg, fCfg, aInfo);
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
        } catch (final IOException e) {
            throw reportIOException(e);
        }
    }

    /**
     * Write Map result.
     *
     * @param occurrences grouping result
     */
    public void write(final Map<GroupId, Integer> occurrences) {
        final var t = new Timer();
        final var file = getFile();
        try (final var csv = getCsvWriter(file)) {
            for (final var entry: occurrences.entrySet()) {
                writeGroupRecord(csv, entry.getKey(), entry.getValue());
            }
        } catch (final IOException e) {
            throw reportIOException(e);
        }
        System.out.println("  Wrote " + file + " in " + t.elapsedDescription(true));
    }

    void writeGroupRecord(final CsvWriter csv, final GroupId groupId, final int hits) throws IOException {
        final List<String> record = new ArrayList<>();
        // - annotation values
        addAnnotationsToRecord(groupId, record);
        // - metadata values
        addMetadataToRecord(groupId, record);
        // - group size (hits/docs)
        record.add(Long.toString(hits));
        csv.writeRecord(record);
    }

    private void addAnnotationsToRecord(final GroupId groupId, final List<String> record) {
        final int[] tokenIds = groupId.getTokenIds();
        if (bCfg.isDatabaseFormat() && tokenIds.length != 0) {
            // When writing database format, simply register to get an ID and write that.
            final int wordID = aInfo.getWordToId().putOrGet(tokenIds);
            record.add(Integer.toString(wordID));
        } else {
            // for each annotation construct a string for the ngram
            final int ngramSize = fCfg.ngramSize();
            for (int i = 0, tokenArrIndex = 0, len = tokenIds.length;
                 tokenArrIndex < len; i++, tokenArrIndex += ngramSize) {
                // get term index for the annotation
                final Terms termIndex = aInfo.getTerms()[i]; // contains id to string mapping
                final String token = writeStringRecord(ngramSize, tokenIds, tokenArrIndex, termIndex);
                record.add(token);
            }
        }
    }

    private static String writeStringRecord(final int ngramSize, final int[] tokenIds, final int tokenArrIndex,
            final Terms termIndex) {
        // map token int ids to their string values
        final String[] tokenList = new String[ngramSize];
        for (int j = 0; j < ngramSize; j++) {
            tokenList[j] = MatchSensitivity.INSENSITIVE.desensitize(termIndex.get(tokenIds[tokenArrIndex + j]));
        }
        // join with a space
        return String.join(" ", tokenList);
    }

    private void addMetadataToRecord(final GroupId groupId, final List<String> record) {
        final int[] metadataValues = groupId.getMetadataValues();
        if (bCfg.isDatabaseFormat() && aInfo.getGroupedMetaIdx().length > 0) {
            // first write out non-grouped metadata
            final int[] idx = aInfo.getNonGroupedMetaIdx();
            for (final int i: idx) {
                // add metadata value for this index
                final String name = fCfg.metadataFields().get(i).name();
                final String metaValue = aInfo.getFreqMetadata().getValue(name, metadataValues[i]);
                record.add(metaValue);
            }
            // then, write the group ID of the grouped metadata
            final int metaId = aInfo.getMetaToId().putOrGet(groupId.getMetadataValues(), aInfo.getGroupedMetaIdx());
            record.add(Integer.toString(metaId));
        } else {
            if (metadataValues != null) {
                for (int i = 0; i < fCfg.metadataFields().size(); i++) {
                    final String name = fCfg.metadataFields().get(i).name();
                    final String metaValue = aInfo.getFreqMetadata().getValue(name, metadataValues[i]);
                    record.add(metaValue);
                }
            }

        }
    }

    private File getFile() {
        final String fileName = fCfg.getReportName() + getExt();
        return new File(bCfg.getOutputDir(), fileName);
    }
}
