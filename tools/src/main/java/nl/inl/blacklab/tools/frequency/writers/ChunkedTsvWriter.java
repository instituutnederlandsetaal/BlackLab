package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.fory.io.ForyInputStream;

import de.siegmar.fastcsv.writer.CsvWriter;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.blacklab.tools.frequency.data.GroupIdHash;
import nl.inl.blacklab.tools.frequency.data.OccurrenceCounts;

public final class ChunkedTsvWriter extends FreqListWriter {
    private final TsvWriter tsvWriter;

    public ChunkedTsvWriter(final BuilderConfig bCfg, final FreqListConfig fCfg, final AnnotationInfo aInfo) {
        super(bCfg, fCfg, aInfo);
        this.tsvWriter = new TsvWriter(bCfg, fCfg, aInfo);
    }

    private File getFile() {
        final String fileName = fCfg.getReportName() + getExt();
        return new File(bCfg.getOutputDir(), fileName);
    }

    // Merge the sorted subgroupings that were written to disk, writing the resulting TSV as we go.
    // This takes very little memory even if the final output file is huge.
    public void write(final List<File> chunkFiles) {
        File file = getFile();
        System.out.println("  Merging " + chunkFiles.size() + " chunk files to produce " + file);
        try (CsvWriter csv = getCsvWriter(file)) {
            int n = chunkFiles.size();
            ForyInputStream[] chunks = new ForyInputStream[n];
            int[] numGroups = new int[n]; // groups per chunk file

            // These hold the index, key and value for the current group from every chunk file
            int[] index = new int[n];
            GroupIdHash[] key = new GroupIdHash[n];
            OccurrenceCounts[] value = new OccurrenceCounts[n];

            try {
                int chunksExhausted = 0;
                for (int i = 0; i < n; i++) {
                    File chunkFile = chunkFiles.get(i);
                    ForyInputStream fis = getForyInputStream(chunkFile);
                    chunks[i] = fis;
                    numGroups[i] = (int) fory.deserialize(fis);
                    // Initialize index, key and value with first group from each file
                    index[i] = 0;
                    key[i] = numGroups[i] > 0 ? (GroupIdHash) fory.deserialize(fis) : null;
                    value[i] = numGroups[i] > 0 ? (OccurrenceCounts) fory.deserialize(fis) : null;
                    if (numGroups[i] == 0)
                        chunksExhausted++;
                }

                // Now, keep merging the "lowest" keys together and advance them,
                // until we run out of groups.
                while (chunksExhausted < n) {
                    // Find lowest key value; we will merge that group next
                    GroupIdHash nextGroupToMerge = null;
                    for (int j = 0; j < n; j++) {
                        if (nextGroupToMerge == null || key[j] != null && key[j].compareTo(nextGroupToMerge) < 0)
                            nextGroupToMerge = key[j];
                    }

                    // Merge all groups with the lowest value,
                    // and advance those chunk files to the next group
                    int hits = 0, docs = 0;
                    for (int j = 0; j < n; j++) {
                        if (key[j] != null && key[j].equals(nextGroupToMerge)) {
                            // Add to merged counts
                            hits += value[j].hits;
                            docs += value[j].docs;
                            // Advance to next group in this chunk
                            index[j]++;
                            boolean noMoreGroupsInChunk = index[j] >= numGroups[j];
                            key[j] = noMoreGroupsInChunk ? null : (GroupIdHash) fory.deserialize(chunks[j]);
                            value[j] = noMoreGroupsInChunk ? null : (OccurrenceCounts) fory.deserialize(chunks[j]);
                            if (noMoreGroupsInChunk)
                                chunksExhausted++;
                        }
                    }

                    // Finally, write the merged group to the output file.
                    if (nextGroupToMerge != null)
                        tsvWriter.writeGroupRecord(csv, nextGroupToMerge, hits);
                }

            } finally {
                for (ForyInputStream chunk: chunks)
                    chunk.close();
            }
        } catch (IOException e) {
            throw reportIOException(e);
        }
    }
}
