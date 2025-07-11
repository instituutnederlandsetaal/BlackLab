package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.util.Timer;

public final class AnnotationWriter extends FreqListWriter {
    private final StringBuilder sb = new StringBuilder();

    public AnnotationWriter(final BuilderConfig bCfg, final FreqListConfig fCfg, final AnnotationInfo aInfo) {
        super(bCfg, fCfg, aInfo);
    }

    public void write() {
        System.out.println("  Writing annotation IDs");
        final var t = new Timer();

        final var file = getFile();
        final var map = aInfo.getWordToId();
        try (final var csv = getCsvWriter(file)) {
            map.forEach((k, v) -> {
                final var record = new ArrayList<String>();
                record.add(Integer.toString(v)); // add ID as first column
                for (int i = 0, len = k.length; i < len; i += fCfg.ngramSize()) {
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
        for (int i = startPos, endPos = startPos + fCfg.ngramSize(); i < endPos; i++) {
            sb.append(tokenIds[i]);
            if (i < endPos - 1) {
                sb.append(',');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private File getFile() {
        final String fileName = fCfg.getReportName() + "_annotations" + getExt();
        return new File(bCfg.getOutputDir(), fileName);
    }
}
