package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.codec.BlackLab40Codec;
import nl.inl.blacklab.codec.BlackLabCodec;
import nl.inl.blacklab.codec.BlackLabCodecUtil;
import nl.inl.blacklab.codec.blacklab50.BlackLab50Codec;
import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.contentstore.ContentStoreIntegrated;
import nl.inl.blacklab.contentstore.ContentStoreSegmentReader;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexIntegrated;
import nl.inl.blacklab.forwardindex.ForwardIndexSegmentReader;
import nl.inl.blacklab.forwardindex.RelationInfoSegmentReader;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataIntegrated;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPatternTags;
import nl.inl.blacklab.codec.BlackLabCodec;

/**
 * A BlackLab index with all files included in the Lucene index.
 */
public class BlackLabIndexIntegrated extends BlackLabIndexAbstract {

    /** Lucene field attribute. Does the field have a forward index?
        If yes, payloads will indicate primary/secondary values. */
    private static final String BLFA_FORWARD_INDEX = "BL_hasForwardIndex";

    /** Lucene field attribute. Does the field have a content store? */
    private static final String BLFA_CONTENT_STORE = "BL_hasContentStore";

    /** Lucene field attribute. How is this relation field encoded?
     *  ("naive-separate-terms" / "single-term" / ...)
     */
    private static final String BLFA_RELATION_STRATEGY = "BL_relationsStrategy";

    /**
     * Does the specified Lucene field have a forward index stored with it?
     *
     * If yes, we can deduce from the payload if a value is the primary value (e.g. original word,
     * to use for concordances, sort, group, etc.) or a secondary value (e.g. stemmed, synonym).
     * This is used because we only store the primary value in the forward index.
     *
     * We need to know this whenever we work with payloads too, so we can skip this indicator.
     * See {@link nl.inl.blacklab.analysis.PayloadUtils}.
     *
     * @param fieldInfo Lucene field to check
     * @return true if it has a forward index
     */
    public static boolean doesFieldHaveForwardIndex(FieldInfo fieldInfo) {
        String v = fieldInfo.getAttribute(BLFA_FORWARD_INDEX);
        return v != null && v.equals("true");
    }

    /**
     * Indicate that this field has a forward index.
     * @param type field type
     */
    public static void setFieldHasForwardIndex(FieldType type) {
        type.putAttribute(BlackLabIndexIntegrated.BLFA_FORWARD_INDEX, "true");
    }

    /**
     * Is the specified field a content store field?
     *
     * @param fieldInfo field to check
     * @return true if it's a content store field
     */
    public static boolean isContentStoreField(FieldInfo fieldInfo) {
        String v = fieldInfo.getAttribute(BLFA_CONTENT_STORE);
        return v != null && v.equals("true");
    }

    /**
     * Set this field type to be a content store field
     * @param type field type
     */
    public static void setContentStoreField(FieldType type) {
        type.putAttribute(BlackLabIndexIntegrated.BLFA_CONTENT_STORE, "true");
    }

    public static RelationsStrategy getRelationsStrategy(FieldInfo fieldInfo) {
        String strategyName = fieldInfo.getAttribute(BLFA_RELATION_STRATEGY);
        if (StringUtils.isEmpty(strategyName))
            return RelationsStrategy.ifNotRecorded();
        return RelationsStrategy.fromName(strategyName);
    }

    /**
     * Set the relations index/search strategy for this relations field
     * @param type field type
     * @param strategy strategy to use
     */
    public static void setRelationsStrategy(FieldType type, RelationsStrategy strategy) {
        type.putAttribute(BlackLabIndexIntegrated.BLFA_RELATION_STRATEGY, strategy.getName());
    }

    /**
     * Get the content store for an index segment.
     *
     * The returned content store should only be used from one thread.
     *
     * @param lrc leafreader context (segment) to get the content store for.
     * @return content store
     */
    public static ContentStoreSegmentReader contentStore(LeafReaderContext lrc) {
        return BlackLabCodecUtil.getStoredFieldsReader(lrc).contentStore();
    }

    /**
     * Get the forward index for an index segment.
     *
     * The returned forward index should only be used from one thread.
     *
     * @param lrc leafreader context (segment) to get the forward index for.
     * @return forward index
     */
    public static ForwardIndexSegmentReader forwardIndex(LeafReaderContext lrc) {
        return BlackLabCodecUtil.getPostingsReader(lrc).forwardIndex();
    }

    /**
     * Is the specified Lucene field a relations field?
     *
     * If yes, we should store relations info for this field.
     *
     * @param fieldInfo Lucene field to check
     * @return true if it's a relations field
     */
    public static boolean isRelationsField(FieldInfo fieldInfo) {
        String[] nameComponents = AnnotatedFieldNameUtil.getNameComponents(fieldInfo.name);
        return nameComponents.length > 1 && nameComponents[1] != null &&
                AnnotatedFieldNameUtil.isRelationAnnotation(nameComponents[1]);
    }

    /**
     * Get the relation info index for an index segment.
     *
     * The returned relation info index should only be used from one thread.
     *
     * @param lrc leafreader context (segment) to get the relation info index for.
     * @return relation info index, or null if not available
     */
    public static RelationInfoSegmentReader relationInfo(LeafReaderContext lrc) {
        return BlackLabCodecUtil.getPostingsReader(lrc).relationInfo();
    }

    /** A list of stored fields that doesn't include content store fields. */
    private final Set<String> allExceptContentStoreFields;

    /** Relation index/search strategy for this index.
     * ("all encoded into one term" / "type and attributes in separate terms" / ...)
     */
    public RelationsStrategy relationsStrategy = RelationsStrategy.ifNotRecorded();

    /** Get the strategy to use for indexing/searching relations. */
    @Override
    public RelationsStrategy getRelationsStrategy() {
        return relationsStrategy;
    }

    BlackLabIndexIntegrated(String name, BlackLabEngine blackLab, IndexReader reader, File indexDir, boolean indexMode, boolean createNewIndex,
            ConfigInputFormat config) throws ErrorOpeningIndex {
        super(name, blackLab, reader, indexDir, indexMode, createNewIndex, config, null);

        // See if this is an old external index. If so, delete the version file,
        // forward indexes and content stores.
        if (indexDir != null && createNewIndex) {
            if (indexDir.exists()) {
                if (indexDir.isDirectory()) {
                    BlackLabIndexExternal.deleteOldIndexFiles(indexDir);
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
        boolean fieldsFounds = false; // is this a completely empty index..? (except for the metadata doc)
        for (LeafReaderContext lrc: reader().leaves()) {
            boolean relStratSet = false;
            for (FieldInfo fi: lrc.reader().getFieldInfos()) {
                if (!isContentStoreField(fi))
                    allExceptContentStoreFields.add(fi.name);

                if (IndexMetadataIntegrated.isMetadataDocField(fi.name))
                    continue;
                fieldsFounds = true;

                // Also determine the relations strategy used when indexing,
                // so we can use the same one for searching.
                if (!createNewIndex && isRelationsField(fi) && !relStratSet) {
                    relationsStrategy = getRelationsStrategy(fi);
                    // We only need to determine the relations strategy once per segment, even if there's multiple annotated fields
                    relStratSet = true;
                }
            }
        }
        if (createNewIndex || !fieldsFounds)
            relationsStrategy = RelationsStrategy.forNewIndex();
    }

    @Override
    protected IndexMetadataWriter getIndexMetadata(boolean createNewIndex, ConfigInputFormat config) {
        if (!createNewIndex)
            return IndexMetadataIntegrated.deserializeFromJsonJaxb(this);
        return IndexMetadataIntegrated.create(this, config);
    }

    @Override
    protected IndexMetadataWriter getIndexMetadata(boolean createNewIndex, File indexTemplateFile) {
        if (indexTemplateFile != null)
            throw new IllegalArgumentException("Template file not supported for integrated index format! Please see the IndexTool documentation for how use the classic index format.");
        return getIndexMetadata(createNewIndex, (ConfigInputFormat)null);
    }

    @Override
    protected void openContentStore(Field field, boolean createNewContentStore, File indexDir) {
        String luceneField = AnnotatedFieldNameUtil.contentStoreField(field.name());
        ContentStore cs = ContentStoreIntegrated.open(reader(), luceneField);
        registerContentStore(field, cs);
    }

    @Override
    public ForwardIndex createForwardIndex(AnnotatedField field) {
        return new ForwardIndexIntegrated(this, field);
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
    public BLSpanQuery tagQuery(QueryInfo queryInfo, String luceneField, String tagNameRegex,
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
    public IndexMetadataIntegrated metadata() {
        return (IndexMetadataIntegrated)super.metadata();
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
                return reader().document(docId);
            } else {
                return reader().document(docId, allExceptContentStoreFields);
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
