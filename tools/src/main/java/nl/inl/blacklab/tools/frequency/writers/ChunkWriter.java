package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.blacklab.tools.frequency.data.GroupId;
import nl.inl.util.Timer;

public final class ChunkWriter extends FreqListWriter {
    private final File tmpDir;

    public ChunkWriter(final FrequencyListConfig cfg, final AnnotationInfo aInfo) {
        super(cfg, aInfo);
        this.tmpDir = new File(cfg.runConfig().outputDir(), "tmp");
    }

    public File write(final Map<GroupId, Integer> occurrences) {
        final var t = new Timer();
        final var file = getFile();
        try (final var os = getOutputStream(file)) {
            // Write keys and values in sorted order, so we can merge later
            os.write(fory.serialize(occurrences.size())); // start with number of groups
            occurrences.forEach((key, value) -> {
                try {
                    os.write(fory.serialize(key));
                    os.write(fory.serialize(value));
                } catch (final IOException e) {
                    throw reportIOException(e);
                }
            });
        } catch (final IOException e) {
            throw reportIOException(e);
        }
        System.out.println("  Wrote chunk file in " + t.elapsedDescription(true));
        return file;
    }

    private File getFile() {
        final String ext = cfg.runConfig().compressed() ? ".chunk.lz4" : ".chunk";
        final String chunkName = cfg.name() + "-" + UUID.randomUUID() + ext;
        return new File(tmpDir, chunkName);
    }
}
