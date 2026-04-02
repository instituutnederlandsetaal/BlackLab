package nl.inl.blacklab.plugins;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFields;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.DocTask;

/** Reads a list of PIDs and removes documents from the index that are not on the list.
 *
 * Useful to propagate removals while synchronizing a BlackLab index with e.g. a
 * document database.
 * (a separate synchronization step would detect new and updated documents and upsert
 * those)
 */
public class RemoveDocIfNotInList extends DocTaskType {

    @Override
    public DocTask docTask(BlackLabIndex index, Map<String, String> args) {
        BlackLabIndexWriter indexWriter = (BlackLabIndexWriter) index;

        if (!args.containsKey("toKeepFile"))
            throw new IllegalArgumentException("Required argument: toKeepFile (file with list of PIDs to keep, one per line)");
        File toKeepFile = new File(args.get("toKeepFile"));
        if (!toKeepFile.exists())
            throw new IllegalArgumentException("To-keep file " + toKeepFile + " does not exist.");
        if (!toKeepFile.canRead())
            throw new IllegalArgumentException("To-keep file " + toKeepFile + " is not readable.");
        File toAddFile = args.containsKey("toAddFile") ? new File(args.get("toAddFile")) : null;
        boolean okayToRemoveMany = Boolean.parseBoolean(args.getOrDefault("okayToRemoveMany", "false"));

        return new DocTask() {
            /** All PIDs to keep */
            Set<String> pidsToKeep;

            /** PIDs found that were not in the "to keep" list and should therefore be removed */
            final Set<String> pidsToRemove = new HashSet<>();

            /** Name of this index' PID field. */
            String pidField;

            @Override
            public void initializeTask() {
                pidField = indexWriter.metadata().metadataFields().pidField().name();

                // Make sure our hash table is large enough that it won't reallocate
                long fileSize = toKeepFile.length();
                int numberOfPids = (int) (fileSize / 31);
                int initialCapacity = numberOfPids * 3 / 2; // initial capacity with load factor 0.75
                pidsToKeep = new HashSet<>(initialCapacity);

                // Read the set of PIDs
                try (Reader reader = new BufferedReader(new FileReader(toKeepFile, StandardCharsets.UTF_8))) {
                    String line;
                    try (BufferedReader br = new BufferedReader(reader)) {
                        while ((line = br.readLine()) != null) {
                            line = line.trim();
                            if (!line.isEmpty())
                                pidsToKeep.add(line);
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Error reading PIDS-to-keep file: " + toKeepFile, e);
                }
            }

            @Override
            public void finishTask() {
                int numberOfDocs = indexWriter.writer().getNumberOfDocs();
                if (!okayToRemoveMany && pidsToRemove.size() > numberOfDocs / 10) {
                    // Something seems wrong; don't mess up the index
                    throw new IllegalStateException("Would remove " + pidsToRemove.size() +  " of " +
                            numberOfDocs + " documents. Refusing to remove more than 10% of documents unless " +
                            "you specify okayToRemoveMany=true");
                }

                System.out.println("Removing " + pidsToRemove.size() + " documents from the index...");
                for (String pid: pidsToRemove) {
                    indexWriter.deleteDocumentByPid(pid);
                }

                // If pidsToAddFile configured: write list of PIDs-to-keep that aren't already in the index
                if (toAddFile != null) {
                    try (Writer writer = new BufferedWriter(
                            new FileWriter(toAddFile, StandardCharsets.UTF_8))) {
                        for (String pid: pidsToKeep) {
                            writer.write(pid);
                            writer.write("\n");
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException("Error writing PIDS-to-add file: " + toAddFile, e);
                    }
                }
            }

            @Override
            public SegmentTask segmentDocTask(LeafReaderContext segment) {
                return segmentDocId -> {
                    try {
                        StoredFields storedFields = segment.reader().storedFields();
                        Document document = storedFields.document(segmentDocId, Set.of(pidField));
                        String pid = document.get(pidField);
                        if (!pidsToKeep.contains(pid))
                            pidsToRemove.add(pid);
                        else
                            pidsToKeep.remove(pid);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
            }
        };
    }
}
