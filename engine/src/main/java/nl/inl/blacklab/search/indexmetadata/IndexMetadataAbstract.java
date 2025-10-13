package nl.inl.blacklab.search.indexmetadata;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.index.DocIndexerLegacy;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.indexers.config.ConfigAnnotatedField;
import nl.inl.blacklab.indexers.config.ConfigAnnotation;
import nl.inl.blacklab.indexers.config.ConfigAnnotationGroup;
import nl.inl.blacklab.indexers.config.ConfigAnnotationGroups;
import nl.inl.blacklab.indexers.config.ConfigCorpus;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.ConfigLinkedDocument;
import nl.inl.blacklab.indexers.config.ConfigMetadataBlock;
import nl.inl.blacklab.indexers.config.ConfigMetadataField;
import nl.inl.blacklab.indexers.config.ConfigMetadataFieldGroup;
import nl.inl.blacklab.indexers.config.ConfigStandoffAnnotations;
import nl.inl.blacklab.indexers.config.TextDirection;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.util.Json;
import nl.inl.util.TimeUtil;

/**
 * Determines the structure of a BlackLab index.
 */
@SuppressWarnings("deprecation")
public abstract class IndexMetadataAbstract implements IndexMetadataWriter {

    protected static final Logger logger = LogManager.getLogger(IndexMetadataAbstract.class);

    /** What keys may occur at top level? */
    protected static final Set<String> KEYS_TOP_LEVEL = new HashSet<>(Arrays.asList(
            "displayName", "description", "contentViewable", "textDirection",
            "documentFormat", "tokenCount", "versionInfo", "fieldInfo"));

    /** What keys may occur under versionInfo? */
    protected static final Set<String> KEYS_VERSION_INFO = new HashSet<>(Arrays.asList(
            "indexFormat", "blackLabBuildTime", "blackLabVersion", "timeCreated",
            "timeModified", "alwaysAddClosingToken", "tagLengthInPayload"));

    /** What keys may occur under fieldInfo? */
    protected static final Set<String> KEYS_FIELD_INFO = new HashSet<>(Arrays.asList(
            "namingScheme", "unknownCondition", "unknownValue",
            "metadataFields", "complexFields", "metadataFieldGroups", "annotationGroups",
            "defaultAnalyzer", "titleField", "authorField", "dateField", MetadataFields.SPECIAL_FIELD_SETTING_PID));

    /** What keys may occur under metadataFieldGroups group? */
    protected static final Set<String> KEYS_METADATA_GROUP = new HashSet<>(Arrays.asList(
            "name", "fields", "addRemainingFields"));

    /** What keys may occur under annotationGroups group? */
    protected static final Set<String> KEYS_ANNOTATION_GROUP = new HashSet<>(Arrays.asList(
            "name", "annotations", "addRemainingAnnotations"));

    /** What keys may occur under metadata field config? */
    protected static final Set<String> KEYS_META_FIELD_CONFIG = new HashSet<>(Arrays.asList(
            "type", "displayName", "uiType",
            "description", "group", "analyzer",
            "unknownValue", "unknownCondition", "values",
            "displayValues", "displayOrder", "valueListComplete"));

    /** What keys may occur under annotated field config? */
    protected static final Set<String> KEYS_ANNOTATED_FIELD_CONFIG = new HashSet<>(Arrays.asList(
            "displayName", "description", "mainProperty",
            "noForwardIndexProps", "displayOrder", "annotations"));

    /** Our index */
    protected final BlackLabIndex index;

    /** Custom properties */
    protected final CustomPropsMap custom = new CustomPropsMap();

    /** When BlackLab.jar was built */
    protected String blackLabBuildTime;

    /** BlackLab version used to (initially) create index */
    protected String blackLabVersion;

    /** Format the index uses */
    protected String indexFormat;

    /** Time at which index was created */
    protected String timeCreated;

    /** Time at which index was created */
    protected String timeModified;

    /**
     * May all users freely retrieve the full content of documents, or is that
     * restricted?
     */
    protected boolean contentViewable = false;

    /**
     * Indication of the document format(s) in this index.
     *
     * This is in the form of a format identifier as understood by the
     * DocumentFormats class (either an abbreviation or a (qualified) class name).
     */
    protected String documentFormat;

    protected long tokenCount = 0;

    /** Our metadata fields */
    protected final MetadataFieldsImpl metadataFields;

    /** Our annotated fields */
    protected final AnnotatedFieldsImpl annotatedFields = new AnnotatedFieldsImpl();

    /** Is this instance frozen, that is, are all mutations disallowed? */
    protected FreezeStatus frozen = new FreezeStatus();

    protected IndexMetadataAbstract(BlackLabIndex index) {
        this.index = index;
        metadataFields = new MetadataFieldsImpl(createMetadataFieldValuesFactory());
        metadataFields.setTopLevelCustom(custom); // for metadata groups
        annotatedFields.setTopLevelCustom(custom); // for annotation groups
    }

    // Methods that read data
    // ------------------------------------------------------------------------------

    protected ObjectNode createEmptyIndexMetadata() {
        ObjectMapper mapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = mapper.createObjectNode();
        jsonRoot.put("displayName", IndexMetadata.indexNameFromDirectory(index.indexDirectory()));
        jsonRoot.put("description", "");
        addVersionInfo(jsonRoot);
        ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
        fieldInfo.putObject("metadataFields");
        fieldInfo.putObject("complexFields");
        return jsonRoot;
    }

    protected ObjectNode createIndexMetadataFromConfig(ConfigInputFormat config) {
        ConfigCorpus corpusConfig = config.getCorpusConfig();
        ObjectMapper mapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = mapper.createObjectNode();
        String displayName = corpusConfig.getDisplayName();
        if (displayName.isEmpty())
            displayName = IndexMetadata.indexNameFromDirectory(index.indexDirectory());
        jsonRoot.put("displayName", displayName);
        jsonRoot.put("description", corpusConfig.getDescription());
        jsonRoot.put("contentViewable", corpusConfig.isContentViewable());
        jsonRoot.put("textDirection", corpusConfig.getTextDirection().getCode());
        jsonRoot.put("documentFormat", config.getName());
        addVersionInfo(jsonRoot);
        ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
        fieldInfo.put("defaultAnalyzer", config.getMetadataDefaultAnalyzer());
        fieldInfo.put("unknownCondition", config.getMetadataDefaultUnknownCondition().stringValue());
        fieldInfo.put("unknownValue", config.getMetadataDefaultUnknownValue());
        for (Entry<String, String> e: corpusConfig.getSpecialFields().entrySet()) {
            fieldInfo.put(e.getKey(), e.getValue());
        }
        ArrayNode metaGroups = fieldInfo.putArray("metadataFieldGroups");
        ObjectNode annotGroups = fieldInfo.putObject("annotationGroups");
        ObjectNode metadata = fieldInfo.putObject("metadataFields");
        ObjectNode annotated = fieldInfo.putObject("complexFields");

        addFieldInfoFromConfig(metadata, annotated, metaGroups, annotGroups, config);
        return jsonRoot;
    }

    @Override
    public AnnotatedFieldsImpl annotatedFields() {
        return annotatedFields;
    }

    /**
     * Detect type by finding the first document that includes this field and
     * inspecting the Fieldable. This assumes that the field type is the same for
     * all documents.
     *
     * @param fieldName the field name to determine the type for
     * @return type of the field (text or numeric)
     */
    protected static FieldType getFieldType(String fieldName) {

        /* NOTE: detecting the field type does not work well.
         * Querying values and deciding based on those is not the right way
         * (you can index ints as text too, after all). Lucene does not
         * store the information in the index (and querying the field type does
         * not return an IntField, DoubleField or such. In effect, it expects
         * the client to know.
         *
         * We have a simple, bad approach based on field name below.
         * The "right way" to do it is to keep a schema of field types during
         * indexing.
         */

        FieldType type = FieldType.TOKENIZED;
        if (fieldName.endsWith("Numeric") || fieldName.endsWith("Num") || fieldName.equals("metadataCid"))
            type = FieldType.NUMERIC;
        return type;
    }

    @Override
    public MetadataFieldsWriter metadataFields() {
        return metadataFields;
    }

    /**
     * Get a description of the index, if specified
     *
     * @return the description
     */
    @Override
    public String description() {
        return custom.get("description", "");
    }

    /**
     * Is the content freely viewable by all users, or is it restricted?
     *
     * @return true if the full content may be retrieved by anyone
     */
    @Override
    public boolean contentViewable() {
        return contentViewable;
    }

    /**
     * What's the text direction of this corpus?
     *
     * @return text direction
     * @deprecated use {@link #custom()} with .get("textDirection", "ltr") instead
     */
    @Override
    @Deprecated
    public TextDirection textDirection() {
        return TextDirection.fromCode(custom.get("textDirection", "ltr"));
    }

    /**
     * What format(s) is/are the documents in?
     *
     * This is in the form of a format identifier as understood by the
     * DocumentFormats class (either an abbreviation or a (qualified) class name).
     *
     * @return the document format(s)
     */
    @Override
    public String documentFormat() {
        return documentFormat;
    }

    /**
     * What version of the index format is this?
     *
     * @return the index format version
     */
    @Override
    public String indexFormat() {
        return indexFormat;
    }

    /**
     * When was this index created?
     *
     * @return date/time stamp
     */
    @Override
    public String timeCreated() {
        return timeCreated;
    }

    /**
     * When was this index last modified?
     *
     * @return date/time stamp
     */
    @Override
    public String timeModified() {
        return timeModified;
    }

    /**
     * When was the BlackLab.jar used for indexing built?
     *
     * @return date/time stamp
     */
    @Override
    public String indexBlackLabBuildTime() {
        return blackLabBuildTime;
    }

    /**
     * When was the BlackLab.jar used for indexing built?
     *
     * @return date/time stamp
     */
    @Override
    public String indexBlackLabVersion() {
        return blackLabVersion;
    }

    @Override
    public String indexBlackLabScmRevision() {
        return "UNKNOWN";
    }

    /**
     * Is this a new, empty index?
     *
     * An empty index is one that doesn't have a main contents field yet, or has a
     * main contents field but no indexed tokens yet.
     *
     * @return true if it is, false if not.
     */
    @Override
    public boolean isNewIndex() {
        return annotatedFields.main() == null || tokenCount == 0;
    }

    @Override
    public long tokenCount() {
        return tokenCount;
    }

    @Override
    public Map<String, CorpusSize.Count> countPerField() {
        return Map.of(mainAnnotatedField().name(), CorpusSize.Count.get(documentCount(), tokenCount));
    }

    // Methods that mutate data
    // ------------------------------------

    /**
     * Return a factory that creates a MetadataFieldValues object.
     *
     * Will either create such an object that uses the indexmetadata file
     * to manage metadata field values, or one that determines the values
     * from the Lucene index directly, using DocValues.
     *
     * @return factory
     */
    protected abstract MetadataFieldValues.Factory createMetadataFieldValuesFactory();

    protected abstract String getLatestIndexFormat();

    protected synchronized AnnotatedFieldImpl getOrCreateAnnotatedField(String name) {
        ensureNotFrozen();
        AnnotatedFieldImpl cfd = null;
        if (annotatedFields.exists(name))
            cfd = ((AnnotatedFieldImpl) annotatedField(name));
        if (cfd == null) {
            cfd = new AnnotatedFieldImpl(name);
            annotatedFields.put(name, cfd);
        }
        return cfd;
    }

    /**
     * Indicate that the index was modified, so that fact will be recorded in the
     * metadata file.
     */
    @Override
    public void updateLastModified() {
        ensureNotFrozen();
        timeModified = TimeUtil.timestamp();
    }

    /**
     * While indexing, check if an annotated field is already registered in the
     * metadata, and if not, add it now.
     *
     * @param fieldWriter field to register
     * @return registered annotated field
     */
    @Override
    public synchronized AnnotatedField registerAnnotatedField(AnnotatedFieldWriter fieldWriter) {
        String fieldName = fieldWriter.name();
        AnnotatedFieldImpl cf;
        if (annotatedFields.exists(fieldName)) {
            cf = annotatedFields.get(fieldName);
        } else {
            ensureNotFrozen();

            // Not registered yet; do so now. Note that we only add the main annotation,
            // not the other annotations, but that's okay; they're not needed at index
            // time and will be detected at search time.
            cf = getOrCreateAnnotatedField(fieldName);
        }

        // Make sure all the annotations, their sensitivities, the offset sensitivity, whether
        // they have a forward index, and the main annotation are all registered correctly.
        for (AnnotationWriter annotationWriter: fieldWriter.annotationWriters()) {
            AnnotationImpl annotation = cf.getOrCreateAnnotation(annotationWriter.name());
            for (String suffix: annotationWriter.sensitivitySuffixes()) {
                annotation.addAlternative(MatchSensitivity.fromLuceneFieldSuffix(suffix));
            }
            if (annotationWriter.includeOffsets())
                annotation.setOffsetsMatchSensitivity(MatchSensitivity.fromLuceneFieldSuffix(annotationWriter.mainSensitivity()));
            annotation.setForwardIndex(annotationWriter.hasForwardIndex());
            annotationWriter.setAnnotation(annotation);
        }
        String mainAnnotName = fieldWriter.mainAnnotation().name();
        cf.getOrCreateAnnotation(mainAnnotName); // create main annotation
        cf.setMainAnnotationName(mainAnnotName); // set main annotation
        fieldWriter.setAnnotatedField(cf);
        return cf;
    }

    @Override
    public synchronized MetadataField registerMetadataField(String fieldName) {
        return metadataFields.register(fieldName);
    }

    /**
     * Set the display name for this index. Only makes sense in index mode where the
     * change will be saved. Usually called when creating an index.
     *
     * @param displayName the display name to set.
     */
    @Override
    public void setDisplayName(String displayName) {
        ensureNotFrozen();
        if (displayName.length() > 80)
            displayName = StringUtils.abbreviate(displayName, 75);
        this.custom.put("displayName", displayName);
    }

    protected void setDescription(String description) {
        custom.put("description", description);
    }

    /**
     * Set a document format (or formats) for this index.
     *
     * This should be a format identifier as understood by the DocumentFormats class
     * (either an abbreviation or a (qualified) class name).
     *
     * It only makes sense to call this in index mode, where this change will be
     * saved.
     *
     * @param documentFormat the document format to store
     */
    @Override
    public void setDocumentFormat(String documentFormat) {
        ensureNotFrozen();
        this.documentFormat = documentFormat;
    }

    @Override
    public void addToTokenCount(long tokensProcessed) {
        ensureNotFrozen();
        tokenCount += tokensProcessed;
    }

    /**
     * Used when creating an index to initialize contentViewable setting. Do not use
     * otherwise.
     *
     * It is also used to support a deprecated configuration setting in BlackLab
     * Server, but this use will eventually be removed.
     *
     * @param contentViewable whether content may be freely viewed
     */
    @Override
    public void setContentViewable(boolean contentViewable) {
        ensureNotFrozen();
        this.contentViewable = contentViewable;
    }

    /**
     * Used when creating an index to initialize textDirection setting. Do not use
     * otherwise.
     *
     * @param textDirection text direction
     * @deprecated use {@link #custom()} with .put("textDirection", textDirection.getCode()) instead
     */
    @Override
    @Deprecated
    public void setTextDirection(TextDirection textDirection) {
        ensureNotFrozen();
        this.custom.put("textDirection", textDirection.getCode());
    }

    /**
     * If the object node contains any keys other than those specified, warn about
     * it
     *
     * @param where where are we in the file (e.g. "top level", "annotated field
     *            'contents'", etc.)
     * @param node node to check
     * @param knownKeys keys that may occur under this node
     */
    protected static void warnUnknownKeys(String where, JsonNode node, Set<String> knownKeys) {
        Iterator<Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if (!knownKeys.contains(key))
                logger.warn("Unknown key " + key + " " + where + " in indexmetadata file");
        }
    }

    protected void addVersionInfo(ObjectNode jsonRoot) {
        ObjectNode versionInfo = jsonRoot.putObject("versionInfo");
        versionInfo.put("blackLabBuildTime", BlackLab.buildTime());
        versionInfo.put("blackLabVersion", BlackLab.version());
        versionInfo.put("timeCreated", TimeUtil.timestamp());
        versionInfo.put("timeModified", TimeUtil.timestamp());
        versionInfo.put("indexFormat", getLatestIndexFormat());
        versionInfo.put("alwaysAddClosingToken", true); // always true, but BL check for it, so required
        versionInfo.put("tagLengthInPayload", true); // always true, but BL check for it, so required
    }

    protected void addFieldInfoFromConfig(ObjectNode metadata, ObjectNode annotated, ArrayNode metaGroups,
            ObjectNode annotGroupsPerField, ConfigInputFormat config) {
        ensureNotFrozen();

        // Add metadata field groups info
        ConfigCorpus corpusConfig = config.getCorpusConfig();
        for (ConfigMetadataFieldGroup g: corpusConfig.getMetadataFieldGroups().values()) {
            ObjectNode h = metaGroups.addObject();
            h.put("name", g.getName());
            if (g.getFields().size() > 0) {
                ArrayNode i = h.putArray("fields");
                for (String f: g.getFields()) {
                    i.add(f);
                }
            }
            if (g.isAddRemainingFields())
                h.put("addRemainingFields", true);
        }

        // Add annotated field annotation groupings info
        for (ConfigAnnotationGroups cfgField: corpusConfig.getAnnotationGroups().values()) {
            ArrayNode annotGroups = annotGroupsPerField.putArray(cfgField.getName());
            if (cfgField.getGroups().size() > 0) {
                for (ConfigAnnotationGroup cfgAnnotGroup: cfgField.getGroups()) {
                    ObjectNode annotGroup = annotGroups.addObject();
                    annotGroup.put("name", cfgAnnotGroup.getName());
                    ArrayNode annots = annotGroup.putArray("annotations");
                    for (String annot: cfgAnnotGroup.getAnnotations()) {
                        annots.add(annot);
                    }
                    if (cfgAnnotGroup.isAddRemainingAnnotations())
                        annotGroup.put("addRemainingAnnotations", true);
                }
            }
        }

        // Add metadata info
        String defaultAnalyzer = config.getMetadataDefaultAnalyzer();
        for (ConfigMetadataBlock b: config.getMetadataBlocks()) {
            for (ConfigMetadataField f: b.getFields()) {
                if (f.isForEach())
                    continue;
                ObjectNode g = metadata.putObject(f.getName());
                g.put("displayName", f.getDisplayName());
                g.put("description", f.getDescription());
                g.put("type", f.getType().stringValue());
                if (!f.getAnalyzer().equals(defaultAnalyzer))
                    g.put("analyzer", f.getAnalyzer());
                g.put("uiType", f.getUiType());
                g.put("unknownCondition", (f.getUnknownCondition() == null ?
                        config.getMetadataDefaultUnknownCondition() : f.getUnknownCondition()).stringValue());
                g.put("unknownValue", f.getUnknownValue() == null ? config.getMetadataDefaultUnknownValue()
                        : f.getUnknownValue());
                ObjectNode h = g.putObject("displayValues");
                for (Entry<String, String> e: f.getDisplayValues().entrySet()) {
                    h.put(e.getKey(), e.getValue());
                }
                ArrayNode i = g.putArray("displayOrder");
                for (String v: f.getDisplayOrder()) {
                    i.add(v);
                }
            }
        }

        // Add annotated field info
        for (ConfigAnnotatedField f: config.getAnnotatedFields().values()) {
            ObjectNode g = annotated.putObject(f.getName());
            g.put("displayName", f.getDisplayName());
            g.put("description", f.getDescription());
            if (!f.getAnnotations().isEmpty())
                g.put("mainProperty", f.getAnnotations().values().iterator().next().getName());
            ArrayNode displayOrder = g.putArray("displayOrder");
            ArrayNode noForwardIndexAnnotations = g.putArray("noForwardIndexProps");
            ArrayNode annotations = g.putArray("annotations");
            for (ConfigAnnotation a: f.getAnnotations().values()) {
                addAnnotationInfo(a, displayOrder, noForwardIndexAnnotations, annotations);
            }
            for (ConfigStandoffAnnotations standoff: f.getStandoffAnnotations()) {
                for (ConfigAnnotation a: standoff.getAnnotations().values()) {
                    addAnnotationInfo(a, displayOrder, noForwardIndexAnnotations, annotations);
                }
            }
        }

        // Also (recursively) add metadata and annotated field config from any linked
        // documents
        for (ConfigLinkedDocument ld: config.getLinkedDocuments().values()) {
            InputFormat inputFormat = DocumentFormats.getFormat(ld.getInputFormatIdentifier()).orElse(null);
            if (inputFormat.isConfigurationBased())
                addFieldInfoFromConfig(metadata, annotated, metaGroups, annotGroupsPerField,
                        inputFormat.getConfig());
        }
    }

    protected void addAnnotationInfo(ConfigAnnotation annotation, ArrayNode displayOrder, ArrayNode noForwardIndexAnnotations,
            ArrayNode annotations) {
        ensureNotFrozen();
        String annotationName = annotation.getName();
        displayOrder.add(annotationName);
        if (!annotation.createForwardIndex())
            noForwardIndexAnnotations.add(annotationName);
        ObjectNode annotationNode = annotations.addObject();
        annotationNode.put("name", annotationName);
        annotationNode.put("displayName", annotation.getDisplayName());
        annotationNode.put("description", annotation.getDescription());
        annotationNode.put("uiType", annotation.getUiType());
        if (annotation.isInternal()) {
            annotationNode.put("isInternal", annotation.isInternal());
        }
        if (getIndexType() == BlackLabIndex.IndexType.INTEGRATED) {
            annotationNode.put("hasForwardIndex", annotation.createForwardIndex());
        }
        if (annotation.getSubAnnotations().size() > 0) {
            ArrayNode subannots = annotationNode.putArray("subannotations");
            for (ConfigAnnotation s: annotation.getSubAnnotations()) {
                if (!s.isForEach()) {
                    addAnnotationInfo(s, displayOrder, noForwardIndexAnnotations, annotations);
                    subannots.add(s.getName());
                }
            }
        }
    }

    @Override
    public CustomPropsMap custom() {
        return custom;
    }

    /**
     * Get the display name for the index.
     *
     * If no display name was specified, returns the name of the index directory.
     *
     * @return the display name
     */
    @Override
    public String displayName() {
        String dispName = custom.get("displayName", "");
        if (dispName.isEmpty())
            dispName = StringUtils.capitalize(index.indexDirectory().getName());
        if (dispName.isEmpty())
            dispName = StringUtils.capitalize(index.indexDirectory().getAbsoluteFile().getParentFile().getName());
        if (dispName.isEmpty())
            dispName = "index";
        return dispName;
    }

    @Override
    public boolean freeze() {
        boolean b = frozen.freeze();
        if (b) {
            annotatedFields.freeze();
            metadataFields.freeze();
        }
        return b;
    }

    @Override
    public boolean isFrozen() {
        return frozen.isFrozen();
    }

    protected void detectMainAnnotation(IndexReader reader) {
        boolean shouldDetectMainAnnotatedField = annotatedFields.main() == null;

        // Detect main contents field and main annotations of annotated fields
        // Detect the main annotations for all annotated fields
        // (looks for fields with char offset information stored)
        AnnotatedFieldImpl mainAnnotatedField = null;
        for (AnnotatedField d: annotatedFields()) {
            if (mainAnnotatedField == null || d.name().equals(DocIndexerLegacy.DEFAULT_CONTENTS_FIELD_NAME))
                mainAnnotatedField = (AnnotatedFieldImpl) d;

            if (tokenCount() > 0) // Only detect if index is not empty
                ((AnnotatedFieldImpl) d).detectMainAnnotation(reader);
        }
        if (shouldDetectMainAnnotatedField)
            annotatedFields.setMainAnnotatedField(mainAnnotatedField);
    }
}
