package nl.inl.blacklab.index;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.MaxDocsReached;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.indexers.config.InputFormatTypeWithConverters;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.util.fileprocessor.FileHandler;
import nl.inl.util.fileprocessor.FileReference;

/**
 * FileProcessor FileHandler that creates a DocIndexer for every file and
 * performs some reporting.
 */
class FileHandlerDocIndexer implements FileHandler {

    private final Indexer indexer;

    private final InputFormat docIndexer;

    public FileHandlerDocIndexer(Indexer indexer, FileConverter.ExtraConverters extraConverters) {
        this.indexer = indexer;
        docIndexer = InputFormatTypeWithConverters.wrap(indexer.getDocIndexer(), extraConverters);
    }

    @Override
    public boolean continueIndexing() {
        return indexer.continueIndexing();
    }

    @Override
    public void file(FileReference file) throws MalformedInputFile, PluginException {
        indexer.listener().fileStarted(file.getPath());
        IndexerStats indexerStats;
        try {
            indexerStats = docIndexer.index(indexer, file);
        } catch (MaxDocsReached e) {
            throw e;
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
