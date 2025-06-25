package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.fory.io.ForyInputStream;

import de.siegmar.fastcsv.writer.CsvWriter;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.data.GroupIdHash;
import nl.inl.blacklab.tools.frequency.data.OccurrenceCounts;

public class ChunkedTsvWriter extends FreqListWriter {
    // Merge the sorted subgroupings that were written to disk, writing the resulting TSV as we go.
    // This takes very little memory even if the final output file is huge.
    public static void write(List<File> chunkFiles, Terms[] terms, MatchSensitivity[] sensitivity,
            BuilderConfig bCfg, FreqListConfig fCfg) {
        File outputFile = new File(bCfg.getOutputDir(),
                fCfg.getReportName() + ".tsv" + (bCfg.isCompressed() ? ".lz4" : ""));
        System.out.println("  Merging " + chunkFiles.size() + " chunk files to produce " + outputFile);
        try (CsvWriter csv = getCsvWriter(outputFile, bCfg.isCompressed())) {
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
                    ForyInputStream fis = getForyInputStream(chunkFile, bCfg.isCompressed());
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
                        TsvWriter.writeGroupRecord(sensitivity, terms, csv, nextGroupToMerge, hits);
                }

            } finally {
                for (ForyInputStream chunk: chunks)
                    chunk.close();
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }
}
