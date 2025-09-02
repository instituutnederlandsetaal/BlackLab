package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

import jakarta.xml.bind.annotation.XmlTransient;
import nl.inl.blacklab.codec.BlackLabCodec;
import nl.inl.blacklab.codec.blacklab50.BlackLab50Codec;
import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.contentstore.ContentStoreIntegrated;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.index.BLFieldTypeLucene;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataImpl;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPatternTags;
import nl.inl.util.VersionFile;

/**
 * A BlackLab index with all files included in the Lucene index.
 */
public class BlackLabIndexImpl extends BlackLabIndexAbstract {

    /**
     * Is the specified Lucene field a relations field?
     * <p>
     * If yes, we should store relations info for this field.
     *
     * @param fieldInfo Lucene field to check
     * @return true if it's a relations field
     */
    public static boolean isRelationsField(FieldInfo fieldInfo) {
        String[] nameComponents = AnnotatedFieldNameUtil.getNameComponents(fieldInfo.name);
        return nameComponents.length > 1 && nameComponents[1] != null && nameComponents[1].equals(
                AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME);
    }

    /** A list of stored fields that doesn't include content store fields. */
    private final Set<String> allExceptContentStoreFields;

    /** Relation index/search strategy for this index.
     * ("all encoded into one term" / "type and attributes in separate terms" / ...)
     */
    public RelationsStrategy relationsStrategy = null;

    /**
     * If this directory contains any external index files/subdirs, delete them.
     * <p>
     * Doesn't delete the Lucene index (Lucene does this when creating a new index in a dir).
     *
     * @param indexDir the directory to clean up
     */
    public static void deleteOldIndexFiles(File indexDir) {
        if (VersionFile.exists(indexDir)) {
            for (File f: Objects.requireNonNull(indexDir.listFiles())) {
                if (f.getName().equals(VersionFile.FILE_NAME)) {
                    if (!f.delete())
                        logger.warn("Could not delete version file " + f);
                } else if (f.getName().matches("(fi|cs)_.+|indexmetadata\\.(ya?ml|json)")) {
                    try {
                        if (f.isDirectory())
                            FileUtils.deleteDirectory(f);
                        else if (!f.delete())
                            logger.warn("Could not delete index metadata file: " + f);
                    } catch (IOException e) {
                        logger.warn("Could not delete subdirectory " + f);
                    }
                }
            }
        }
    }

    /** Get the strategy to use for indexing/searching relations. */
    @Override
    public RelationsStrategy getRelationsStrategy() {
        return relationsStrategy;
    }

    BlackLabIndexImpl(String name, BlackLabEngine blackLab, IndexReader reader, File indexDir, boolean indexMode, boolean createNewIndex,
            ConfigInputFormat config) throws ErrorOpeningIndex {
        super(name, blackLab, reader, indexDir, indexMode, createNewIndex, config);

        // See if this is an old external index. If so, delete the version file,
        // forward indexes and content stores.
        if (indexDir != null && createNewIndex) {
            if (indexDir.exists()) {
                if (indexDir.isDirectory()) {
                    deleteOldIndexFiles(indexDir);
                } else {
                    throw new ErrorOpeningIndex("Index directory " + indexDir + " is not a directory.");
                }
            }
        }

        // Determine the list of all fields in the index, but skip fields that
        // represent a content store as they contain very large values (i.e. the
        // whole input document) we don't generally want returned when requesting
        // a Document)
        allExceptContentStoreFields = new HashSet<>();
        boolean anyFieldsFounds = false; // is this a completely empty index..? (except for the metadata doc)
        for (LeafReaderContext lrc: reader().leaves()) {
            boolean relStratSet = false;
            for (FieldInfo fi: lrc.reader().getFieldInfos()) {

                // Keep a list of non-content store fields
                if (!BLFieldTypeLucene.isContentStoreField(fi))
                    allExceptContentStoreFields.add(fi.name);

                // Ignore special fields that only exist in the metadata document
                if (IndexMetadataImpl.isMetadataDocField(fi.name))
                    continue;
                anyFieldsFounds = true;

                // Also determine the relations strategy used when indexing,
                // so we can use the same one for searching.
                if (!createNewIndex && isRelationsField(fi) && !relStratSet) {
                    try {
                        relationsStrategy = BLFieldTypeLucene.getRelationsStrategy(fi);
                    } catch (Exception e) {
                        throw new ErrorOpeningIndex(e.getMessage(), e);
                    }
                    // We only need to determine the relations strategy once per segment, even if there's multiple annotated fields
                    relStratSet = true;
                }
            }
        }
        if (createNewIndex || !anyFieldsFounds)
            relationsStrategy = RelationsStrategy.forNewIndex();
        if (relationsStrategy == null)
            relationsStrategy = RelationsStrategy.ifNotRecorded();
    }

    @Override
    protected IndexMetadataWriter getIndexMetadata(boolean createNewIndex, ConfigInputFormat config) {
        if (!createNewIndex)
            return IndexMetadataImpl.deserializeFromJsonJaxb(this);
        return IndexMetadataImpl.create(this, config);
    }

    @Override
    protected void openContentStore(Field field, boolean createNewContentStore, File indexDir) {
        String luceneField = AnnotatedFieldNameUtil.contentStoreField(field.name());
        ContentStore cs = new ContentStoreIntegrated(leafReaderLookup, luceneField);
        contentStores.put(field, cs);
    }

    @Override
    protected void customizeIndexWriterConfig(IndexWriterConfig config) {
        if (!(config.getCodec() instanceof BlackLabCodec))
            config.setCodec(new BlackLab50Codec()); // our own custom codec (extended from Lucene)

        // disabling this can speed up indexing a bit but also uses a lot of file descriptors;
        // it can be useful to see individual files during development. maybe make this configurable?
        config.setUseCompoundFile(false);
    }

    @Override
    public ForwardIndexAccessor forwardIndexAccessor(String searchField) {
        return new ForwardIndexAccessorIntegrated(this, annotatedField(searchField));
    }

    @Override
    public BLSpanQuery tagQuery(QueryInfo queryInfo, AnnotationSensitivity luceneField, String tagNameRegex,
            Map<String, String> attributes, TextPatternTags.Adjust adjust, String captureAs) {
        // Note: tags are always indexed as a forward relation (source always occurs before target)
        RelationInfo.SpanMode spanMode = switch (adjust) {
            case LEADING_EDGE -> RelationInfo.SpanMode.SOURCE;
            case TRAILING_EDGE -> RelationInfo.SpanMode.TARGET;
            default -> RelationInfo.SpanMode.FULL_SPAN;
        };

        return new SpanQueryRelations(queryInfo, luceneField,
                RelationUtil.fullTypeRegex(RelationUtil.CLASS_INLINE_TAG, tagNameRegex),
                attributes, SpanQueryRelations.Direction.FORWARD, spanMode, captureAs, null);
    }

    @Override
    public IndexType getType() {
        return IndexType.INTEGRATED;
    }

    @Override
    public IndexMetadataImpl metadata() {
        return (IndexMetadataImpl)super.metadata();
    }

    @Override
    public boolean needsPrimaryValuePayloads() {
        // we need these because we store the forward index when the segment is about to be written,
        // at which point this information would otherwise be lost.
        return true;
    }

    @Override
    @XmlTransient
    public Query getAllRealDocsQuery() {
        // NOTE: we cannot use Lucene's MatchAllDocsQuery because we need to skip the index metadata document.

        // Get all documents, but make sure to skip the index metadata document.
        // We do this by finding all docs that don't have the metadata marker.
        // (previously, we used new DocValuesFieldExistsQuery(mainAnnotatedField().tokenLengthField())),
        //  so all docs that don't have a value for the main annotated field, but that's not really correct)
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.SHOULD);
        builder.add(metadata().metadataDocQuery(), BooleanClause.Occur.MUST_NOT);
        return builder.build();
    }

    @Override
    public Document luceneDoc(int docId, boolean includeContentStores) {
        try {
            if (includeContentStores) {
                return reader().storedFields().document(docId);
            } else {
                return reader().storedFields().document(docId, allExceptContentStoreFields);
            }
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    @Override
    public void delete(Query q) {
        if (!indexMode())
            throw new UnsupportedOperationException("Cannot delete documents, not in index mode");
        try {
            logger.debug("Delete query: " + q);
            indexWriter.deleteDocuments(q);
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

}
