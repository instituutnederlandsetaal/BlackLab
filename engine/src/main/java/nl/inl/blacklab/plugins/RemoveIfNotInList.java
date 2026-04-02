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
import java.util.Optional;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;

/** Reads a list of PIDs and removes documents from the index that are not on the list.
 *
 * Useful to propagate removals while synchronizing a BlackLab index with e.g. a
 * document database.
 * (a separate synchronization step would detect new and updated documents and upsert
 * those)
 */
public class RemoveIfNotInList extends IndexDocTask {

    /** All PIDs to keep */
    Set<String> pidsToKeep;

    /** PIDs found that were not in the "to keep" list and should therefore be removed */
    Set<String> pidsToRemove = new HashSet<>();

    /** Name of this index' PID field. */
    String pidField;

    public void initializeTask(BlackLabIndex index) {
        // Read PIDs from Duct export file
        File pidsToKeepFile = cfgFile("pidsToKeepFile", "pids-to-keep.txt");

        pidField = ((BlackLabIndexWriter) index).metadata().metadataFields().pidField().name();

        // Make sure our hash table is large enough that it won't reallocate
        long fileSize = pidsToKeepFile.length();
        int numberOfPids = (int)(fileSize / 31);
        int initialCapacity = numberOfPids * 3 / 2; // initial capacity with load factor 0.75
        pidsToKeep = new HashSet<>(initialCapacity);

        // Read the set of PIDs
        try (Reader reader = new BufferedReader(new FileReader(pidsToKeepFile, StandardCharsets.UTF_8))) {
            String line;
            try (BufferedReader br = new BufferedReader(reader)) {
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty())
                        pidsToKeep.add(line);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error reading PIDS-to-keep file: " + pidsToKeepFile, e);
        }
    }

    public void finishTask(BlackLabIndex index) {
        boolean okayToRemoveMany = cfgBool("okayToRemoveMany", false);
        BlackLabIndexWriter indexWriter = (BlackLabIndexWriter) index;
        if (!okayToRemoveMany && pidsToRemove.size() > indexWriter.writer().getNumberOfDocs() / 10) {
            // Something seems wrong; don't mess up the index
            throw new IllegalStateException("Refusing to remove more than 10% of documents");
        }

        System.out.println("Removing " + pidsToRemove.size() + " documents from the index...");
        for (String pid : pidsToRemove) {
            indexWriter.deleteDocumentByPid(pid);
        }

        // If pidsToAddFile configured: write list of PIDs-to-keep that aren't already in the index
        Optional<File> pidsToAddFile = cfgFile("pidsToAddFile");
        if (pidsToAddFile.isPresent()) {
            try (Writer writer = new BufferedWriter(new FileWriter(pidsToAddFile.get(), StandardCharsets.UTF_8))) {
                for (String pid: pidsToKeep) {
                    writer.write(pid);
                    writer.write("\n");
                }
            } catch (Exception e) {
                throw new IllegalStateException("Error writing PIDS-to-add file: " + pidsToAddFile, e);
            }
        }
    }

    public void document(LeafReaderContext segment, int segmentDocId) throws PluginException {
        try {
            String pid = segment.reader().storedFields().document(segmentDocId, Set.of(pidField)).get(pidField);
            if (!pidsToKeep.contains(pid))
                pidsToRemove.add(pid);
            else
                pidsToKeep.remove(pid);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
