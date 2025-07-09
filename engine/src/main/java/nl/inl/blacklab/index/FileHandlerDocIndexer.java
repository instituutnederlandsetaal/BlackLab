package nl.inl.blacklab.index;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.util.FileProcessor;
import nl.inl.util.FileReference;

/**
 * FileProcessor FileHandler that creates a DocIndexer for every file and
 * performs some reporting.
 */
class FileHandlerDocIndexer implements FileProcessor.FileHandler {

    private final Indexer indexer;

    public FileHandlerDocIndexer(Indexer indexer) {
        this.indexer = indexer;
    }

    @Override
    public void file(FileReference file) throws MalformedInputFile, PluginException {
        InputFormat inputFormat = DocumentFormats.getFormat(indexer.getFormatIdentifier()).orElseThrow();
        try (DocIndexer docIndexer = inputFormat.createDocIndexer(indexer, file)) {
            if (docIndexer == null) {
                throw new PluginException(
                        "Could not instantiate DocIndexer: " + indexer.getFormatIdentifier() + ", " + file.getPath());
            }
            if (file.getAssociatedFile() != null)
                docIndexer.setDocumentDirectory(file.getAssociatedFile().getParentFile()); // for XInclude resolution

            if (docIndexer.continueIndexing()) {
                indexer.listener().fileStarted(file.getPath());
                int docsDoneBefore = docIndexer.numberOfDocsDone();
                long tokensDoneBefore = docIndexer.numberOfTokensDone();
                try {
                    docIndexer.index();
                } catch (Exception e) {
                    throw new ErrorIndexingFile("Error while indexing input file: " + file.getPath(), e);
                }
                indexer.listener().fileDone(file.getPath());

                int docsDoneAfter = docIndexer.numberOfDocsDone();
                if (docsDoneAfter == docsDoneBefore) {
                    IndexerImpl.logger.warn("No docs found in " + file.getPath() + "; wrong format?");
                }
                long tokensDoneAfter = docIndexer.numberOfTokensDone();
                if (tokensDoneAfter == tokensDoneBefore) {
                    IndexerImpl.logger.warn("No words indexed in " + file.getPath() + "; wrong format?");
                }
            }
        }
    }
}
