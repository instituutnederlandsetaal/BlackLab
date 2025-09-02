package nl.inl.blacklab.tools.frequency.writers.database;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.data.helper.DatabaseHelper;
import nl.inl.blacklab.tools.frequency.writers.FreqListWriter;
import nl.inl.util.Timer;

public final class MetaGroupWriter extends FreqListWriter {

    public MetaGroupWriter(final FrequencyListConfig cfg) {
        super(cfg);
    }

    public void write(final DatabaseHelper helper) {
        if (cfg.ngramSize() != 1) {
            System.out.println("  Ngram size is not 1, skipping grouped metadata IDs report.");
            return;
        }

        final var t = new Timer();

        final var file = getFile();
        final var map = helper.metaToId().getMap();
        if (map.isEmpty()) {
            System.out.println("  No metadata found, skipping grouped metadata IDs report.");
            return;
        }
        // sort map for consistent output
        try (final var csv = getCsvWriter(file)) {
            map.forEach((k, v) -> {
                final var record = new ArrayList<String>();
                record.add(String.valueOf(v)); // add ID as first column
                for (int i = 0; i < k.length; i++) {
                    // add metadata value for this index
                    final var idxSelection = helper.groupedMetadata();
                    final String name = cfg.metadata().get(idxSelection[i]).name();
                    final String metaValue = helper.metadataTerms().getValue(name, k[i]);
                    record.add(metaValue);
                }
                csv.writeRecord(record);
            });
        } catch (final IOException e) {
            throw reportIOException(e);
        }

        System.out.println("  Wrote grouped metadata IDs in " + t.elapsedDescription(true));
    }

    private File getFile() {
        final String fileName = cfg.name() + "_metadata" + getExt();
        return new File(cfg.runConfig().outputDir(), fileName);
    }
}
