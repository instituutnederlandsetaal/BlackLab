package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.blacklab.tools.frequency.data.GroupCounts;
import nl.inl.blacklab.tools.frequency.data.GroupId;
import nl.inl.util.Timer;

public final class ChunkWriter extends FreqListWriter {

    public ChunkWriter(final BuilderConfig bCfg, final FreqListConfig fCfg, final AnnotationInfo aInfo) {
        super(bCfg, fCfg, aInfo);
    }

    public File write(final Map<GroupId, GroupCounts> occurrences) {
        final var t = new Timer();
        final var file = getFile();
        try (final var os = getOutputStream(file)) {
            // Write keys and values in sorted order, so we can merge later
            os.write(fory.serialize(occurrences.size())); // start with number of groups
            for (final var entry: occurrences.entrySet()) {
                os.write(fory.serialize(entry.getKey()));
                os.write(fory.serialize(entry.getValue()));
            }
        } catch (IOException e) {
            throw reportIOException(e);
        }
        System.out.println("  Wrote chunk file in " + t.elapsedDescription(true));
        return file;
    }

    private File getFile() {
        final String ext = bCfg.isCompressed() ? ".chunk.lz4" : ".chunk";
        final String chunkName = fCfg.getReportName() + "-" + UUID.randomUUID() + ext;
        return new File(bCfg.getOutputDir() + "/tmp", chunkName);
    }
}
