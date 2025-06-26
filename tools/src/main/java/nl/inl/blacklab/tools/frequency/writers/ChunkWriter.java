package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.blacklab.tools.frequency.data.GroupIdHash;
import nl.inl.blacklab.tools.frequency.data.OccurrenceCounts;

public final class ChunkWriter extends FreqListWriter {

    public ChunkWriter(final BuilderConfig bCfg, final FreqListConfig fCfg, final AnnotationInfo aInfo) {
        super(bCfg, fCfg, aInfo);
    }

    public void write(final File file, final Map<GroupIdHash, OccurrenceCounts> occurrences) {
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
    }
}
