package nl.inl.blacklab.tools.frequency.writers.database;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.data.IdMap;
import nl.inl.blacklab.tools.frequency.writers.FreqListWriter;
import nl.inl.util.Timer;

public final class AnnotationWriter extends FreqListWriter {
    private final StringBuilder sb = new StringBuilder();

    public AnnotationWriter(final FrequencyListConfig cfg) {
        super(cfg);
    }

    public void write(final IdMap wordToId) {
        final var t = new Timer();
        if (cfg.annotations().isEmpty()) {
            System.out.println("  No annotations found, skipping annotation IDs report.");
            return;
        }

        final var file = getFile();
        final var map = wordToId.getMap();
        try (final var csv = getCsvWriter(file)) {
            map.forEach((k, v) -> {
                final var record = new ArrayList<String>();
                record.add(Integer.toString(v)); // add ID as first column
                for (int i = 0, len = k.length; i < len; i += cfg.ngramSize()) {
                    record.add(writeIdRecord(k, i));
                }
                csv.writeRecord(record);
            });
        } catch (final IOException e) {
            throw reportIOException(e);
        }

        System.out.println("  Wrote annotation IDs in " + t.elapsedDescription(true));
    }

    /**
     * Write in a database suitable format using IDs instead of strings.
     */
    private String writeIdRecord(final int[] tokenIds, final int startPos) {
        sb.setLength(0);
        sb.append('{');
        for (int i = startPos, endPos = startPos + cfg.ngramSize(); i < endPos; i++) {
            sb.append(tokenIds[i]);
            if (i < endPos - 1) {
                sb.append(',');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private File getFile() {
        final String fileName = cfg.name() + "_annotations" + getExt();
        return new File(cfg.runConfig().outputDir(), fileName);
    }
}
