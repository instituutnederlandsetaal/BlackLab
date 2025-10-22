package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.siegmar.fastcsv.writer.CsvWriter;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.data.GroupId;
import nl.inl.blacklab.tools.frequency.data.helper.IndexHelper;
import nl.inl.util.Timer;

/**
 * Writes frequency results to a TSV file.
 */
public final class TsvWriter extends FreqListWriter {
    private final IndexHelper helper;

    public TsvWriter(final FrequencyListConfig cfg, final IndexHelper helper) {
        super(cfg);
        this.helper = helper;
    }

    private static String writeStringRecord(final int ngramSize, final int[] tokenIds, final int tokenArrIndex,
            final Terms terms) {
        // map token int ids to their string values
        final String[] tokenList = new String[ngramSize];
        for (int j = 0; j < ngramSize; j++) {
            tokenList[j] = MatchSensitivity.INSENSITIVE.desensitize(terms.get(tokenIds[tokenArrIndex + j]));
        }
        // join with a space
        return String.join(" ", tokenList);
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
        final int[] sorting = groupId.sorting();
        if (cfg.runConfig().compressed() && sorting.length != 0) {
            // When writing database format, simply register to get an ID and write that.
            final int wordID = helper.database().wordToId().putOrGet(sorting);
            record.add(Integer.toString(wordID));
        } else {
            // for each annotation construct a string for the ngram
            final int ngramSize = cfg.ngramSize();
            for (int i = 0, tokenArrIndex = 0, len = sorting.length;
                 tokenArrIndex < len; i++, tokenArrIndex += ngramSize) {
                // get term index for the annotation
                final var terms = helper.annotations().forwardIndices().get(i).terms(); // contains id to string mapping
                final String token = writeStringRecord(ngramSize, sorting, tokenArrIndex, terms);
                record.add(token);
            }
        }
    }

    private void addMetadataToRecord(final GroupId groupId, final List<String> record) {
        final var database = helper.database();
        final int[] metadataValues = groupId.metadata();
        if (cfg.runConfig().databaseFormat() && database.groupedMetadata().length > 0) {
            // first write out non-grouped metadata
            final var idx = database.ungroupedMetadata();
            for (final int i: idx) {
                // add metadata value for this index
                final String name = cfg.metadata().get(i).name();
                final String metaValue = database.metadataTerms().getValue(name, metadataValues[i]);
                record.add(metaValue);
            }
            // then, write the group ID of the grouped metadata
            final int metaId = database.metaToId().putOrGet(groupId.metadata(), database.groupedMetadata());
            record.add(Integer.toString(metaId));
        } else {
            if (metadataValues != null) {
                for (int i = 0; i < cfg.metadata().size(); i++) {
                    final String name = cfg.metadata().get(i).name();
                    final String metaValue = database.metadataTerms().getValue(name, metadataValues[i]);
                    record.add(metaValue);
                }
            }

        }
    }

    private File getFile() {
        final String fileName = cfg.name() + getExt();
        return new File(cfg.runConfig().outputDir(), fileName);
    }
}
