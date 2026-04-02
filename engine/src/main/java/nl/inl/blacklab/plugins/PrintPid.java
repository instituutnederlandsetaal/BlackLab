package nl.inl.blacklab.plugins;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.DocTask;
import nl.inl.blacklab.search.indexmetadata.MetadataField;

/** Reads a list of PIDs and removes documents from the index that are not on the list.
 *
 * Useful to propagate removals while synchronizing a BlackLab index with e.g. a
 * document database.
 * (a separate synchronization step would detect new and updated documents and upsert
 * those)
 */
public class PrintPid extends DocTaskType {
    @Override
    public DocTask docTask(BlackLabIndex index, Map<String, String> args) {
        return new DocTask() {
            /** Name of this index' PID field. */
            String pidField;

            @Override
            public void initializeTask() {
                MetadataField metadataField = ((BlackLabIndexWriter) index).metadata().metadataFields().pidField();
                if (metadataField == null)
                    throw new PluginException("Corpus has no configured pid field");
                pidField = metadataField.name();
            }

            @Override
            public SegmentTask segmentDocTask(LeafReaderContext segment) {
                return segmentDocId -> {
                    try {
                        String pid = segment.reader().storedFields().document(segmentDocId, Set.of(pidField)).get(pidField);
                        System.out.println(pid);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
            }
        };
    }
}
