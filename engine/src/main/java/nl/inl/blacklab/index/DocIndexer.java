package nl.inl.blacklab.index;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.util.fileprocessor.FileReference;

/** Indexes files of a certain type.
 *
 * Implementations are not thread-safe,
 * but should be reusable for multiple file.
 */
public interface DocIndexer extends AutoCloseable {

    /**
     * Index documents contained in a file.
     *
     * @param file the file to index
     * @throws ErrorIndexingFile if there was an error indexing the file
     */
    IndexerStats index(FileReference file) throws ErrorIndexingFile;

    @Override
    void close();
}
