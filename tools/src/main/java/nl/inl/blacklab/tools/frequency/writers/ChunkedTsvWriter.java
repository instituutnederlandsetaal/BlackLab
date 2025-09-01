package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.fory.io.ForyInputStream;

import de.siegmar.fastcsv.writer.CsvWriter;
import nl.inl.blacklab.tools.frequency.config.Config;
import nl.inl.blacklab.tools.frequency.config.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.blacklab.tools.frequency.data.BufferedForyInputStream;
import nl.inl.blacklab.tools.frequency.data.GroupId;
import nl.inl.util.Timer;

public final class ChunkedTsvWriter extends FreqListWriter {
    private final TsvWriter tsvWriter;

    public ChunkedTsvWriter(final Config bCfg, final FrequencyListConfig fCfg, final AnnotationInfo aInfo) {
        super(bCfg, fCfg, aInfo);
        this.tsvWriter = new TsvWriter(bCfg, fCfg, aInfo);
    }

    private File getFile() {
        final String fileName = fCfg.name() + getExt();
        return new File(cfg.outputDir(), fileName);
    }

    // Merge the sorted subgroupings that were written to disk, writing the resulting TSV as we go.
    // This takes very little memory even if the final output file is huge.
    public void write(final List<File> chunkFiles) {
        final var t = new Timer();
        final var file = getFile();
        System.out.println("  Merging " + chunkFiles.size() + " chunk files to produce " + file);
        try (final CsvWriter csv = getCsvWriter(file)) {
            final int n = chunkFiles.size();
            final ForyInputStream[] chunks = new BufferedForyInputStream[n];
            final int[] numGroups = new int[n]; // groups per chunk file

            // These hold the index, key and value for the current group from every chunk file
            final int[] index = new int[n];
            final GroupId[] key = new GroupId[n];
            final int[] value = new int[n];

            try {
                int chunksExhausted = 0;
                for (int i = 0; i < n; i++) {
                    final File chunkFile = chunkFiles.get(i);
                    final ForyInputStream fis = getForyInputStream(chunkFile);
                    chunks[i] = fis;
                    numGroups[i] = (int) fory.deserialize(fis);
                    // Initialize index, key and value with first group from each file
                    index[i] = 0;
                    key[i] = numGroups[i] > 0 ? (GroupId) fory.deserialize(fis) : null;
                    value[i] = numGroups[i] > 0 ? (int) fory.deserialize(fis) : 0;
                    if (numGroups[i] == 0)
                        chunksExhausted++;
                }

                // Now, keep merging the "lowest" keys together and advance them,
                // until we run out of groups.
                while (chunksExhausted < n) {
                    // Find lowest key value; we will merge that group next
                    GroupId nextGroupToMerge = null;
                    for (int j = 0; j < n; j++) {
                        if (nextGroupToMerge == null || key[j] != null && key[j].compareTo(nextGroupToMerge) < 0)
                            nextGroupToMerge = key[j];
                    }

                    // Merge all groups with the lowest value,
                    // and advance those chunk files to the next group
                    int hits = 0;
                    for (int j = 0; j < n; j++) {
                        if (key[j] != null && key[j].equals(nextGroupToMerge)) {
                            // Add to merged counts
                            hits += value[j];
                            // Advance to next group in this chunk
                            index[j]++;
                            final boolean noMoreGroupsInChunk = index[j] >= numGroups[j];
                            key[j] = noMoreGroupsInChunk ? null : (GroupId) fory.deserialize(chunks[j]);
                            value[j] = noMoreGroupsInChunk ? 0 : (int) fory.deserialize(chunks[j]);
                            if (noMoreGroupsInChunk)
                                chunksExhausted++;
                        }
                    }

                    // Finally, write the merged group to the output file.
                    if (nextGroupToMerge != null)
                        tsvWriter.writeGroupRecord(csv, nextGroupToMerge, hits);
                }

            } finally {
                for (final var chunk: chunks)
                    chunk.close();
            }
        } catch (final IOException e) {
            throw reportIOException(e);
        }
        System.out.println("  Merged chunk files in " + t.elapsedDescription(true));
    }
}
