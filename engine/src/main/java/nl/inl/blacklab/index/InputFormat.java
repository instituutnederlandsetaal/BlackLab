package nl.inl.blacklab.index;

import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.util.fileprocessor.FileReference;

/**
 * Description of a supported input format
 */
public interface InputFormat {

    /**
     * Create a DocIndexer for a file.
     *
     * @param indexer our indexer
     * @param file file to index
     * @return the DocIndexer
     */
    DocIndexer createDocIndexer(DocWriter indexer, FileReference file);

    String getIdentifier();

    String getDisplayName();

    String getDescription();

    String getHelpUrl();

    boolean isVisible();

    default boolean isError() { return getErrorMessage() != null; }

    default String getErrorMessage() {
        return null;
    }

    default boolean isConfigurationBased() { return getConfig() != null; }

    default ConfigInputFormat getConfig() { return null; }

}
