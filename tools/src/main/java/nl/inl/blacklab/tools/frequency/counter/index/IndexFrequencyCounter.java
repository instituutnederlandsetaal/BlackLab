package nl.inl.blacklab.tools.frequency.counter.index;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMapUnsafe;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.counter.FrequencyCounter;
import nl.inl.blacklab.tools.frequency.data.GroupId;
import nl.inl.blacklab.tools.frequency.data.helper.IndexHelper;
import nl.inl.blacklab.tools.frequency.writers.ChunkWriter;
import nl.inl.blacklab.tools.frequency.writers.ChunkedTsvWriter;
import nl.inl.util.LuceneUtil;
import nl.inl.util.Timer;

/**
 * More optimized version of FrequencyCounter.
 * Takes shortcuts to be able to process huge corpora without
 * running out of memory, at the expense of genericity.
 * Major changes:
 * - store metadata values as strings, not PropertyValue
 * - always group on annotations first, then metadata fields
 * - don't create HitGroups, return Map with counts directly
 * - don't check if we exceed maxHitsToCount
 * - always process all documents (no document filter query)
 * - return sorted map, so we can perform sub-groupings and merge them later
 * (uses ConcurrentSkipListMap, or alternatively wraps a TreeMap at the end;
 * note that using ConcurrentSkipListMap has consequences for the compute() method, see there)
 */
public final class IndexFrequencyCounter extends FrequencyCounter {
    private final ChunkWriter chunkWriter;
    private final ChunkedTsvWriter chunkedTsvWriter;

    public IndexFrequencyCounter(final BlackLabIndex index, final FrequencyListConfig cfg, final IndexHelper helper) {
        super(index, cfg, helper);
        this.chunkWriter = new ChunkWriter(cfg);
        this.chunkedTsvWriter = new ChunkedTsvWriter(cfg, helper);
    }

    @Override
    public void count() {
        super.count(); // prints debug info

        // Use specifically optimized CalcTokenFrequencies
        final List<Integer> docIds = getDocIds(index, cfg);

        // Create tmp dir for the chunk files
        final File tmpDir = new File(cfg.runConfig().outputDir(), "tmp");
        if (!tmpDir.exists() && !tmpDir.mkdir())
            throw new RuntimeException("Could not create tmp dir: " + tmpDir);

        // Process the documents in parallel runs. After each run, check the size of the grouping,
        // and write it as a sorted chunk file if it exceeds the configured size.
        // At the end we will merge all the chunks to get the final result.
        final List<File> chunkFiles = generateChunks(docIds);

        // Did we write intermediate chunk files that have to be merged?
        if (!chunkFiles.isEmpty()) {
            // Yes, merge all the chunk files. Because they are sorted, this will consume very little memory,
            // even if the final output file is huge.
            chunkedTsvWriter.write(chunkFiles);

            // Remove chunk files
            for (final File chunkFile: chunkFiles) {
                if (!chunkFile.delete())
                    System.err.println("Could not delete: " + chunkFile);
            }
        }
        if (!tmpDir.delete())
            System.err.println("Could not delete: " + tmpDir);
    }

    private List<File> generateChunks(final List<Integer> docIds) {
        // This is where we store our groups while we're computing/gathering them.
        // Maps from group Id to number of hits and number of docs
        // ConcurrentMap because we're counting in parallel.
        final ConcurrentMap<GroupId, Integer> occurrences = new ConcurrentHashMapUnsafe<>();

        final int docsInParallel = cfg.runConfig().docsInParallel();
        final List<File> chunkFiles = new ArrayList<>();

        for (int i = 0; i < docIds.size(); i += docsInParallel) {
            final int runEnd = Math.min(i + docsInParallel, docIds.size());
            final List<Integer> docIdsInChunk = docIds.subList(i, runEnd);

            // Process current run of documents and add to grouping
            final var t = new Timer();
            processDocsParallel(docIdsInChunk, occurrences);
            System.out.println("  Processed docs " + i + "-" + runEnd + ", " + occurrences.size() + " entries in "
                    + t.elapsedDescription(true));
            writeOccurences(docIds, occurrences, runEnd, chunkFiles);
        }
        return chunkFiles;
    }

    /**
     * Write the occurrences to a file. Either a chunk file, or the final output file.
     */
    private void writeOccurences(
            final List<Integer> docIds, final Map<GroupId, Integer> occurrences,
            final int runEnd,
            final List<File> chunkFiles) {
        // If the grouping has gotten too large, write it to file so we don't run out of memory.
        final boolean groupingTooLarge = occurrences.size() > cfg.runConfig().groupsPerChunk();
        final boolean isFinalRun = runEnd >= docIds.size();

        if (isFinalRun && chunkFiles.isEmpty()) {
            // There's only one chunk. We can skip writing intermediate file and write result directly.
            tsvWriter.write(occurrences);
            occurrences.clear();
        } else if (groupingTooLarge || isFinalRun) {
            // Sort our map now.
            final var t = new Timer();
            final var sorted = occurrences.entrySet()
                    .parallelStream()
                    .collect(Collectors.toConcurrentMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            Integer::sum,
                            ConcurrentSkipListMap::new
                    ));
            System.out.println("  Sorted entries in " + t.elapsedDescription(true));
            // Write chunk files, to be merged at the end
            final var chunkFile = chunkWriter.write(sorted);
            chunkFiles.add(chunkFile);
            // free memory, allocate new on next iteration
            occurrences.clear();
        }
    }

    /**
     * Get the token frequencies for the given query and hit property.
     *
     * @param occurrences grouping to add to
     */
    @SuppressWarnings("DuplicatedCode")
    private void processDocsParallel(
            final List<Integer> docIds,
            final Map<GroupId, Integer> occurrences
    ) {
        docIds.parallelStream().forEach(docId -> {
            try {
                final var doc = new DocumentFrequencyCounter(docId, index, cfg, helper);
                doc.process(occurrences);
            } catch (final IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        });
    }
}
