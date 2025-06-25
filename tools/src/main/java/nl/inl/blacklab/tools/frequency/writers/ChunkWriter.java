package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import nl.inl.blacklab.tools.frequency.data.GroupIdHash;
import nl.inl.blacklab.tools.frequency.data.OccurrenceCounts;

public class ChunkWriter extends FreqListWriter {

    public static void write(File file, Map<GroupIdHash, OccurrenceCounts> occurrences, boolean compress) {
        try (OutputStream os = prepareStream(file, compress)) {
            // Write keys and values in sorted order, so we can merge later
            os.write(fory.serialize(occurrences.size())); // start with number of groups
            for (Map.Entry<GroupIdHash, OccurrenceCounts> entry: occurrences.entrySet()) {
                GroupIdHash key = entry.getKey();
                OccurrenceCounts value = entry.getValue();
                os.write(fory.serialize(key));
                os.write(fory.serialize(value));
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }
}
