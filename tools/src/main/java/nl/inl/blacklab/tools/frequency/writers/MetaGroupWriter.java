package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.util.Timer;

public final class MetaGroupWriter extends FreqListWriter {

    public MetaGroupWriter(final BuilderConfig bCfg, final FreqListConfig fCfg, final AnnotationInfo aInfo) {
        super(bCfg, fCfg, aInfo);
    }

    public void write() {
        final var t = new Timer();

        final var file = getFile();
        final var map = aInfo.getMetaToId().getMap();
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
                    final int[] idxSelection = aInfo.getGroupedMetaIdx();
                    final String name = fCfg.metadataFields().get(idxSelection[i]).name();
                    final String metaValue = aInfo.getFreqMetadata().getValue(name, k[i]);
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
        final String fileName = fCfg.getReportName() + "_metadata" + getExt();
        return new File(bCfg.getOutputDir(), fileName);
    }
}
