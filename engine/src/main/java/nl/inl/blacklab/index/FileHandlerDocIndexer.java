package nl.inl.blacklab.index;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.util.fileprocessor.FileHandler;
import nl.inl.util.fileprocessor.FileReference;

/**
 * FileProcessor FileHandler that creates a DocIndexer for every file and
 * performs some reporting.
 */
class FileHandlerDocIndexer implements FileHandler {

    private final Indexer indexer;

    public FileHandlerDocIndexer(Indexer indexer) {
        this.indexer = indexer;
    }

    @Override
    public boolean continueIndexing() {
        return indexer.continueIndexing();
    }

    @Override
    public void file(FileReference file) throws MalformedInputFile, PluginException {
        InputFormat inputFormat = DocumentFormats.getFormat(indexer.getFormatIdentifier()).orElseThrow();
        try (DocIndexer docIndexer = inputFormat.createDocIndexer(indexer)) {
            if (docIndexer == null) {
                throw new PluginException(
                        "Could not instantiate DocIndexer: " + indexer.getFormatIdentifier() + ", " + file.getPath());
            }

            indexer.listener().fileStarted(file.getPath());
            IndexerStats indexerStats;
            try {
                indexerStats = docIndexer.index(file);
            } catch (Exception e) {
                throw new ErrorIndexingFile("Error while indexing input file: " + file.getPath(), e);
            }
            indexer.listener().fileDone(file.getPath());

            if (indexerStats.documents() == 0) {
                IndexerImpl.logger.warn("No docs found in " + file.getPath() + "; wrong format?");
            }
            if (indexerStats.tokens() == 0) {
                IndexerImpl.logger.warn("No words indexed in " + file.getPath() + "; wrong format?");
            }
        }
    }
}
