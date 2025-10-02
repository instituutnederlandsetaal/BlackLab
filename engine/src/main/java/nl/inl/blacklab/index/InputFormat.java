package nl.inl.blacklab.index;

import nl.inl.blacklab.indexers.config.ConfigInputFormat;

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
    DocIndexer createDocIndexer(DocWriter indexer);

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
