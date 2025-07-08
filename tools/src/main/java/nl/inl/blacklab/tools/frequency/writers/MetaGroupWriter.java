package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;

import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.util.Timer;

public final class MetaGroupWriter extends FreqListWriter {

    public MetaGroupWriter(final BuilderConfig bCfg, final FreqListConfig fCfg, final AnnotationInfo aInfo) {
        super(bCfg, fCfg, aInfo);
    }

    public void write() {
        System.out.println("  Writing grouped metadata IDs");
        final var t = new Timer();

        final var file = getFile();
        final var map = aInfo.getMetaToId();
        try (final var csv = getCsvWriter(file)) {
            map.forEach((k, v) -> {
                k.add(String.valueOf(v));
                csv.writeRecord(k);
            });
        } catch (final IOException e) {
            throw reportIOException(e);
        }

        System.out.println("  Wrote grouped metadata IDs in " + t.elapsedDescription(true));
    }

    private File getFile() {
        final String fileName = fCfg.getReportName() + "_metadata_group" + getExt();
        return new File(bCfg.getOutputDir(), fileName);
    }
}
