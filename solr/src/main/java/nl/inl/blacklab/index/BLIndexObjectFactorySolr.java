package nl.inl.blacklab.index;

import org.apache.lucene.index.IndexWriter;

import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;

/**
 * Factory for objects related to indexing to Solr.
 */
public class BLIndexObjectFactorySolr implements BLIndexObjectFactory {
    public static BLIndexObjectFactorySolr INSTANCE = new BLIndexObjectFactorySolr();

    private BLIndexObjectFactorySolr() {}

    @Override
    public BLInputDocument createInputDocument() {
        return new BLInputDocumentSolr();
    }

    @Override
    public BLFieldType fieldTypeMetadata(boolean tokenized) {
        // ignored in solr path, see BLInputDocumentSolr.
        return BLFieldTypeLucene.metadata(tokenized);
    }

    @Override
    public BLFieldType fieldTypeContentStore() {
        // ignored in solr path, see BLInputDocumentSolr.
        return BLFieldTypeLucene.contentStore();
    }

    @Override
    public BLFieldType fieldTypeAnnotationSensitivity(boolean offsets, boolean forwardIndex,
            RelationsStrategy relationsStrategy) {
        // ignored in solr path, see BLInputDocumentSolr.
        return BLFieldTypeLucene.annotationSensitivity(offsets, forwardIndex, relationsStrategy);
    }

    @Override
    public BLFieldType fieldTypeIndexMetadataMarker() {
        // ignored in solr path, see BLInputDocumentSolr.
        return fieldTypeMetadata(false);
    }

    @Override
    public BLIndexWriterProxy indexWriterProxy(IndexWriter luceneIndexWriter, BlackLabIndexWriter indexWriter) {
        return new BLIndexWriterProxySolr(indexWriter);
    }
}
