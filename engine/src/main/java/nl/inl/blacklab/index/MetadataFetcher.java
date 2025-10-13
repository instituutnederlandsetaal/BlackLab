package nl.inl.blacklab.index;

/**
 * A metadata fetcher can fetch the metadata for a document from some external
 * source (a file, the network, etc.) and add it to the Lucene document.
 */
public abstract class MetadataFetcher implements AutoCloseable {

    public final DocIndexer docIndexer;

    protected MetadataFetcher(DocIndexerLegacy docIndexer) {
        this.docIndexer = docIndexer;
    }

    /**
     * Fetch the metadata for the document currently being indexed and add it to the
     * document as indexed fields.
     */
    public abstract void addMetadata();

    /**
     * Close the fetcher, releasing any resources it holds
     *
     */
    @Override
    public void close() {
        // Nothing, by default
    }

}
