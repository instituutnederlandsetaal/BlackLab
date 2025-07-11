package nl.inl.blacklab.tools.frequency.builder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.data.GroupId;
import nl.inl.blacklab.tools.frequency.writers.ChunkWriter;
import nl.inl.blacklab.tools.frequency.writers.ChunkedTsvWriter;
import nl.inl.util.LuceneUtil;
import nl.inl.util.Timer;

/**
 * More optimized version of HitGroupsTokenFrequencies.
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
public final class IndexBasedBuilder extends FreqListBuilder {
    private final ChunkWriter chunkWriter;
    private final ChunkedTsvWriter chunkedTsvWriter;
    private final Set<String> termFrequencies; // used for cutoff

    public IndexBasedBuilder(final BlackLabIndex index, final BuilderConfig bCfg, final FreqListConfig fCfg) {
        super(index, bCfg, fCfg);
        this.chunkWriter = new ChunkWriter(bCfg, fCfg, aInfo);
        this.chunkedTsvWriter = new ChunkedTsvWriter(bCfg, fCfg, aInfo);
        this.termFrequencies = getTermFrequencies(index, fCfg);
    }

    private Set<String> getTermFrequencies(final BlackLabIndex index, final FreqListConfig fCfg) {
        final Set<String> termFrequencies = new ObjectOpenHashSet<>();
        if (fCfg.cutoff() != null) {
            final var t = new Timer();
            final var sensitivity = aInfo.getCutoffAnnotation().sensitivity(MatchSensitivity.SENSITIVE);
            final var searcher = new IndexSearcher(index.reader());
            final Map<String, Integer> termFrequenciesMap = LuceneUtil.termFrequencies(searcher, null, sensitivity,
                    Collections.emptySet());
            // Only save the strings when the frequency is above the cutoff
            for (final Map.Entry<String, Integer> entry: termFrequenciesMap.entrySet()) {
                if (entry.getValue() >= fCfg.cutoff().count()) {
                    termFrequencies.add(entry.getKey());
                }
            }
            final String logging = " Retrieved " + termFrequenciesMap.size() + " term frequencies " +
                    "for annotation '" + fCfg.cutoff().annotation() + "' with " +
                    termFrequencies.size() + " above cutoff in " + t.elapsedDescription(true);
            System.out.println(logging);
        }
        return termFrequencies;
    }

    @Override
    public void makeFrequencyList() {
        super.makeFrequencyList(); // prints debug info

        // Use specifically optimized CalcTokenFrequencies
        final List<Integer> docIds = getDocIds();

        // Create tmp dir for the chunk files
        final File tmpDir = new File(bCfg.getOutputDir(), "tmp");
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
        final ConcurrentMap<GroupId, Integer> occurrences = new ConcurrentHashMap<>();

        final int docsToProcessInParallel = bCfg.getDocsToProcessInParallel();
        final List<File> chunkFiles = new ArrayList<>();

        for (int rep = 0; rep < bCfg.getRepetitions(); rep++) { // FOR DEBUGGING

            for (int i = 0; i < docIds.size(); i += docsToProcessInParallel) {
                final int runEnd = Math.min(i + docsToProcessInParallel, docIds.size());
                final List<Integer> docIdsInChunk = docIds.subList(i, runEnd);

                // Process current run of documents and add to grouping
                final var t = new Timer();
                processDocsParallel(docIdsInChunk, occurrences);
                System.out.println("  Processed docs " + i + "-" + runEnd + ", " + occurrences.size() + " entries in "
                        + t.elapsedDescription(true));
                writeOccurences(docIds, occurrences, rep, runEnd, chunkFiles);
            }
        }
        return chunkFiles;
    }

    /**
     * Write the occurrences to a file. Either a chunk file, or the final output file.
     */
    private void writeOccurences(
            final List<Integer> docIds, final ConcurrentMap<GroupId, Integer> occurrences, final int rep,
            final int runEnd,
            final List<File> chunkFiles) {
        // If the grouping has gotten too large, write it to file so we don't run out of memory.
        final boolean groupingTooLarge = occurrences.size() > bCfg.getGroupsPerChunk();
        final boolean isFinalRun = rep == bCfg.getRepetitions() - 1 && runEnd >= docIds.size();

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
                            (e1, e2) -> e1,
                            ConcurrentSkipListMap::new
                    ));
            System.out.println("  Sorted ConcurrentSkipListMap in " + t.elapsedDescription(true));
            // Write chunk files, to be merged at the end
            final var chunkFile = chunkWriter.write(sorted);
            chunkFiles.add(chunkFile);
            // free memory, allocate new on next iteration
            occurrences.clear();
        }
    }

    /**
     * Get all document IDs matching the filter.
     * If no filter is defined, return all document IDs.
     */
    private List<Integer> getDocIds() {
        final var t = new Timer();
        final var docIds = new ArrayList<Integer>();
        if (fCfg.filter() != null) {
            try {
                final Query q = LuceneUtil.parseLuceneQuery(index, fCfg.filter(), index.analyzer(), "");
                index.queryDocuments(q).forEach(d -> docIds.add(d.docId()));
            } catch (final ParseException e) {
                throw new RuntimeException(e);
            }
        } else {
            // No filter: include all documents.
            index.forEachDocument((__, id) -> docIds.add(id));
        }
        System.out.println("  Retrieved " + docIds.size() + " documents IDs with filter='" + fCfg.filter() + "' in "
                + t.elapsedDescription(true));
        return docIds;
    }

    /**
     * Get the token frequencies for the given query and hit property.
     *
     * @param occurrences grouping to add to
     */
    @SuppressWarnings("DuplicatedCode")
    private void processDocsParallel(
            final List<Integer> docIds,
            final ConcurrentMap<GroupId, Integer> occurrences
    ) {
        docIds.parallelStream().forEach(docId -> {
            try {
                final var doc = new DocumentIndexBasedBuilder(docId, index, bCfg, fCfg, aInfo);
                doc.process(occurrences, termFrequencies);
            } catch (final IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        });
    }
}
