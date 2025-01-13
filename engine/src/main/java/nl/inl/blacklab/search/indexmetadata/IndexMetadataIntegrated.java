package nl.inl.blacklab.search.indexmetadata;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.indexers.config.ConfigAnnotatedField;
import nl.inl.blacklab.indexers.config.ConfigAnnotationGroup;
import nl.inl.blacklab.indexers.config.ConfigAnnotationGroups;
import nl.inl.blacklab.indexers.config.ConfigCorpus;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.ConfigLinkedDocument;
import nl.inl.blacklab.indexers.config.ConfigMetadataBlock;
import nl.inl.blacklab.indexers.config.ConfigMetadataField;
import nl.inl.blacklab.indexers.config.ConfigMetadataFieldGroup;
import nl.inl.blacklab.indexers.config.TextDirection;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.util.Json;
import nl.inl.util.LuceneUtil;
import nl.inl.util.TimeUtil;

/**
 * Implementation of IndexMetadata, which determines the structure of a BlackLab index. 
 * See {@link IndexMetadata}
 */
@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({
    "custom", "contentViewable", "documentFormat", "versionInfo",
    "metadataFields", "annotatedFields", "documentFormatConfig", "indexFlags"
})
public class IndexMetadataIntegrated implements IndexMetadataWriter {

    /** What is the current index format? */
    public static final String LATEST_INDEX_FORMAT = "4";

    /** Index flag indicating whether we index relations twice, once with and once without attributes,
     *  and also if attribute names are preceded by RelationUtil.CH_NAME_START.
     *  ("true" if yes, "" if no)
     *  (no is for older pre-release versions of relations indexes; this flag will be removed in the future)
     */
    public static final String IFL_INDEX_RELATIONS_TWICE = "index_relations_twice";

    //private static final Logger logger = LogManager.getLogger(IndexMetadataIntegrated.class);

    public static IndexMetadataIntegrated deserializeFromJsonJaxb(BlackLabIndex index) {
        try {
            Integer docId = MetadataDocument.getMetadataDocId(index.reader());
            IndexMetadataIntegrated metadata;
            if (docId == null) {
                // No metadata document found. Instantiate default.
                metadata = new IndexMetadataIntegrated(index, null);
            } else {
                // Load and deserialize metadata document.
                String json = MetadataDocument.getMetadataJson(index.reader(), docId);
                metadata = Json.getJaxbReader().readValue(
                        new StringReader(json), IndexMetadataIntegrated.class);
                metadata.fixAfterDeserialization(index, docId);
            }
            return metadata;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static IndexMetadataIntegrated create(BlackLabIndex index, ConfigInputFormat config) {
        return new IndexMetadataIntegrated(index, config);
    }

    /** Is this one of the special fields that only occur in the index metadata document? */
    public static boolean isMetadataDocField(String luceneFieldName) {
        return luceneFieldName.startsWith(MetadataDocument.INDEX_METADATA_FIELD_PREFIX);
    }

    public Query metadataDocQuery() {
        return metadataDocument.query();
    }

    /**
     * Manages the index metadata document.
     *
     * We store the metadata in a special document. This class takes care of that.
     */
    private static class MetadataDocument {

        /** How to recognize field related to index metadata */
        private static final String INDEX_METADATA_FIELD_PREFIX = "__index_metadata";

        /** Name in our index for the JAXB metadata field */
        private static final String METADATA_FIELD_NAME = INDEX_METADATA_FIELD_PREFIX + "__";

        /** Index metadata document gets a marker field so we can find it again (value same as field name) */
        private static final String METADATA_MARKER = INDEX_METADATA_FIELD_PREFIX + "_marker__";

        private static final TermQuery METADATA_DOC_QUERY = new TermQuery(new Term(METADATA_MARKER, METADATA_MARKER));

        public static String getMetadataJson(IndexReader reader, int docId) throws IOException {
            return reader.document(docId).get(METADATA_FIELD_NAME);
        }

        public static Integer getMetadataDocId(IndexReader reader) throws IOException {
            IndexSearcher searcher = new IndexSearcher(reader);
            final List<Integer> docIds = new ArrayList<>();
            searcher.search(METADATA_DOC_QUERY, new LuceneUtil.SimpleDocIdCollector(docIds));
            if (docIds.isEmpty())
                return null;
            if (docIds.size() > 1)
                throw new RuntimeException("Multiple index metadata found!");
            return docIds.get(0);
        }

        private int metadataDocId = -1;

        public Query query() {
            return METADATA_DOC_QUERY;
        }

        public void saveToIndex(BlackLabIndexWriter indexWriter, IndexMetadataIntegrated metadata) {
            try {
                // Serialize metadata to JSON
                String metadataJson = serializeToJson(metadata);

                // Write debug files
                File tmpDir = new File(System.getProperty("java.io.tmpdir"));
                File debugJackson = new File(tmpDir, "debug-metadata.json");
                FileUtils.writeStringToFile(debugJackson, metadataJson, StandardCharsets.UTF_8);

                updateMetadataDoc(indexWriter, metadataJson);
            } catch (IOException e) {
                throw new RuntimeException("Error saving index metadata", e);
            }
        }

        public void saveToIndex(BlackLabIndexWriter indexWriter, String metadataJson) {
            try {
                updateMetadataDoc(indexWriter, metadataJson);
            } catch (IOException e) {
                throw new RuntimeException("Error saving index metadata", e);
            }
        }

        private void updateMetadataDoc(BlackLabIndexWriter indexWriter, String metadataJson) throws IOException {
            // Create a metadata document with the metadata JSON, config format file,
            // and a marker field to we can find it again
            BLInputDocument indexmetadataDoc = indexWriter.indexObjectFactory().createInputDocument();
            indexmetadataDoc.addStoredField(METADATA_FIELD_NAME, metadataJson);
            indexmetadataDoc.addField(METADATA_MARKER, METADATA_MARKER, indexWriter.indexObjectFactory().fieldTypeIndexMetadataMarker());
            indexWriter.writer().updateDocument(METADATA_DOC_QUERY.getTerm(), indexmetadataDoc);
        }

        private String serializeToJson(IndexMetadataIntegrated metadata) {
            try {
                return Json.getJaxbWriter().writeValueAsString(metadata);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public int getDocumentId() {
            return metadataDocId;
        }
    }

    private void fixAfterDeserialization(BlackLabIndex index, int metadataDocId) {
        metadataDocument.metadataDocId = metadataDocId;

        this.index = index;

        indexWriter = index.indexMode() ? (BlackLabIndexWriter)index : null;
        tokenCount = 0;
        documentCount = 0;
        synchronized (this) {
            tokenCountCalculated = false;
        }
        // (already set) metadataDocument = new MetadataDocument();

        annotatedFields.fixAfterDeserialization(index, this);
        MetadataFieldValues.Factory factory = createMetadataFieldValuesFactory();
        metadataFields.fixAfterDeserialization(this, factory);
        if (!index.indexMode())
            freeze();
    }

    /** Our index */
    @XmlTransient
    protected BlackLabIndex index;

    /** Corpus-level custom properties */
    private final CustomPropsMap custom = new CustomPropsMap();

    @XmlAccessorType(XmlAccessType.FIELD)
    static class VersionInfo {
        /** When BlackLab.jar was built */
        public String blackLabBuildTime;

        /** BlackLab version used to (initially) create index */
        public String blackLabVersion;

        /** Scm revision (i.e. Git hash) used to (initially) create index */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String blackLabScmRevision;

        /** Format the index uses */
        public String indexFormat;

        /** Time at which index was created */
        public String timeCreated;

        /** Time at which index was created */
        public String timeModified;

        public void populateWithDefaults() {
            blackLabBuildTime = BlackLab.buildTime();
            blackLabVersion = BlackLab.version();
            blackLabScmRevision = BlackLab.getBuildScmRevision();
            timeCreated =  TimeUtil.timestamp();
            timeModified =  TimeUtil.timestamp();
            indexFormat =  LATEST_INDEX_FORMAT;
        }
    }

    private final VersionInfo versionInfo = new VersionInfo();

    /** May all users freely retrieve the full content of documents, or is that restricted? */
    private boolean contentViewable = false;

    /**
     * Indication of the document format(s) in this index.
     *
     * This is in the form of a format identifier as understood by the
     * DocumentFormats class (either an abbreviation or a (qualified) class name).
     */
    private String documentFormat;

    @XmlTransient
    protected long tokenCount = 0;

    @XmlTransient
    protected Map<String, Long> tokenCountPerField = new LinkedHashMap<>();

    /** Have we determined our tokenCount from the index? (done lazily) */
    @XmlTransient
    private boolean tokenCountCalculated;

    /** Our metadata fields */
    protected MetadataFieldsImpl metadataFields = null;

    /** Our annotated fields */
    protected final AnnotatedFieldsImpl annotatedFields = new AnnotatedFieldsImpl();

    /** How many documents with values for the main annotated field are in our index */
    @XmlTransient
    private int documentCount;

    /** Contents of the documentFormat config file at index creation time. */
    @SuppressWarnings("unused")
    @JsonProperty("documentFormatConfig")
    private String documentFormatConfigFileContents = "(not set)";

    @XmlTransient
    private BlackLabIndexWriter indexWriter;

    @XmlTransient
    private final MetadataDocument metadataDocument = new MetadataDocument();

    /** Is this instance frozen, that is, are all mutations disallowed? */
    @XmlTransient
    private FreezeStatus frozen = new FreezeStatus();

    /** Free-form flags that indicate how indexing was done.
     *
     * This can be used to deal with slight differences in index format,
     * e.g. whether inline tags are indexed once or twice (once with, once
     * without attributes) without having to change the index format version.
     *
     * Use sparingly, and flags should generally be temporary, to be removed
     * when it is no longer needed.
     */
    private Map<String, String> indexFlags = new HashMap<>();

    // For JAXB deserialization
    @SuppressWarnings("unused")
    IndexMetadataIntegrated() {}

    /**
     * Create index metadata object for a new index.
     *
     * Either based on config if supplied, or populated with default values.
     */
    private IndexMetadataIntegrated(BlackLabIndex index, ConfigInputFormat config) {
        this.index = index;
        metadataFields = new MetadataFieldsImpl(createMetadataFieldValuesFactory());
        metadataFields.setTopLevelCustom(custom); // for special fields, metadata groups
        annotatedFields.setTopLevelCustom(custom); // for annotation groups

        this.indexWriter = index.indexMode() ? (BlackLabIndexWriter)index : null;

        // Create new index metadata from config
        File dir = index.indexDirectory();
        if (config == null)
            populateWithDefaults(index);
        else
            populateFromConfig(config, dir);

        // Indicate that we're indexing relations twice now, once with and once without attributes
        setIndexFlag(IFL_INDEX_RELATIONS_TWICE, Boolean.TRUE.toString());

        documentFormatConfigFileContents = config == null ? "(no config)" : config.getOriginalFileContents();
        if (index.indexMode())
            save(); // save debug file if any

        // During indexing, return a default field config if you try to get a missing field,
        // so not all metadata fields have to be declared in advance (useful with forEach).
        metadataFields.setThrowOnMissingField(!index.indexMode());
    }

    private void populateFromConfig(ConfigInputFormat config, File indexDirectory) {
        ensureNotFrozen();
        ConfigCorpus corpusConfig = config.getCorpusConfig();
        String displayName = corpusConfig.getDisplayName();
        if (StringUtils.isEmpty(displayName))
            displayName = IndexMetadata.indexNameFromDirectory(indexDirectory);
        custom.put("displayName", displayName);
        custom.put("description", corpusConfig.getDescription());
        custom.put("textDirection", corpusConfig.getTextDirection().getCode());
        for (Map.Entry<String, String> e: corpusConfig.getSpecialFields().entrySet()) {
            if (!e.getKey().equals(MetadataFields.SPECIAL_FIELD_SETTING_PID))
                custom.put(e.getKey(), e.getValue());
        }
        custom.put("unknownCondition", config.getMetadataDefaultUnknownCondition().stringValue());
        custom.put("unknownValue", config.getMetadataDefaultUnknownValue());

        addGroupsInfoFromConfig(config);

        contentViewable =  corpusConfig.isContentViewable();
        documentFormat = config.getName();
        versionInfo.populateWithDefaults();
        metadataFields.setDefaultAnalyzer(config.getMetadataDefaultAnalyzer());
        if (corpusConfig.getSpecialFields().containsKey(MetadataFields.SPECIAL_FIELD_SETTING_PID))
            metadataFields.setPidField(corpusConfig.getSpecialFields().get(MetadataFields.SPECIAL_FIELD_SETTING_PID));

        addFieldInfoFromConfig(config);
    }

    private void addFieldInfoFromConfig(ConfigInputFormat config) {
        // Add metadata info
        for (ConfigMetadataBlock b: config.getMetadataBlocks()) {
            for (ConfigMetadataField f: b.getFields()) {
                if (f.isForEach())
                    continue;
                MetadataFieldImpl metadataField = metadataFields.addFromConfig(f);
                // We don't keep track of metadata field values in the integrated
                // index format because it cannot change during indexing.
                // Instead we will use DocValues to get the field values when necessary.
                metadataField.setKeepTrackOfValues(false);
            }
        }

        // Add annotated field info
        for (ConfigAnnotatedField f: config.getAnnotatedFields().values()) {
            annotatedFields.addFromConfig(f);
        }

        // Also (recursively) add metadata and annotated field config from any linked
        // documents
        for (ConfigLinkedDocument ld: config.getLinkedDocuments().values()) {
            InputFormat inputFormat = DocumentFormats.getFormat(ld.getInputFormatIdentifier()).orElse(null);
            if (inputFormat.isConfigurationBased()) {
                addFieldInfoFromConfig(inputFormat.getConfig());
            }
        }

    }

    private void addGroupsInfoFromConfig(ConfigInputFormat config) {
        // Metadata field groups
        ConfigCorpus corpusConfig = config.getCorpusConfig();
        Map<String, MetadataFieldGroupImpl> groups = new LinkedHashMap<>();
        for (ConfigMetadataFieldGroup g: corpusConfig.getMetadataFieldGroups().values()) {
            MetadataFieldGroupImpl group = new MetadataFieldGroupImpl(g.getName(), g.getFields(),
                    g.isAddRemainingFields());
            groups.put(group.name(), group);
        }
        metadataFields.setMetadataGroups(groups);

        // Annotation groups
        custom.put("annotationGroups", new LinkedHashMap<>());
        for (ConfigAnnotationGroups cfgField: corpusConfig.getAnnotationGroups().values()) {
            String fieldName = cfgField.getName();
            List<AnnotationGroup> annotGroups = new ArrayList<>();
            for (ConfigAnnotationGroup cfgAnnotGroup: cfgField.getGroups()) {
                String groupName = cfgAnnotGroup.getName();
                List<String> annotations = cfgAnnotGroup.getAnnotations();
                boolean addRemaining = cfgAnnotGroup.isAddRemainingAnnotations();
                annotGroups.add(new AnnotationGroup(fieldName, groupName, annotations, addRemaining));
            }
            this.annotatedFields.putAnnotationGroups(fieldName, new AnnotationGroups(fieldName, annotGroups));
        }

        // Also (recursively) add groups config from any linked documents
        for (ConfigLinkedDocument ld: config.getLinkedDocuments().values()) {
            InputFormat inputFormat = DocumentFormats.getFormat(ld.getInputFormatIdentifier()).orElse(null);
            if (inputFormat.isConfigurationBased())
                addGroupsInfoFromConfig(inputFormat.getConfig());
        }
    }

    private void populateWithDefaults(BlackLabIndex index) {
        ensureNotFrozen();
        File dir = index.indexDirectory();
        if (dir != null)
            custom.put("displayName", IndexMetadata.indexNameFromDirectory(dir));
        custom.put("description", "");
        custom.put("textDirection", "ltr");
        versionInfo.populateWithDefaults();
        metadataFields.clearSpecialFields();
        custom.put("annotationGroups", new LinkedHashMap<>());
        custom.put("metadataFieldGroups", new LinkedHashMap<>());
    }

    @Override
    public void setIndexFlag(String name, String value) {
        indexFlags.put(name, value);
    }

    @Override
    public String indexFlag(String name) {
        return indexFlags.getOrDefault(name, "");
    }

    @Override
    public AnnotatedFieldsImpl annotatedFields() {
        return annotatedFields;
    }

    @Override
    public MetadataFieldsImpl metadataFields() {
        return metadataFields;
    }

    /**
     * Get a description of the index, if specified
     *
     * @return the description
     */
    @Override
    public String description() {
        return custom().get("description", "");
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
     * @deprecated use {@link #custom()} and get("textDirection", "ltr") instead
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
        return versionInfo.indexFormat;
    }

    /**
     * When was this index created?
     *
     * @return date/time stamp
     */
    @Override
    public String timeCreated() {
        return versionInfo.timeCreated;
    }

    /**
     * When was this index last modified?
     *
     * @return date/time stamp
     */
    @Override
    public String timeModified() {
        return versionInfo.timeModified;
    }

    /**
     * When was the BlackLab.jar used for indexing built?
     *
     * @return date/time stamp
     */
    @Override
    public String indexBlackLabBuildTime() {
        return versionInfo.blackLabBuildTime;
    }

    /**
     * When was the BlackLab.jar used for indexing built?
     *
     * @return date/time stamp
     */
    @Override
    public String indexBlackLabVersion() {
        return versionInfo.blackLabVersion;
    }

    /**
     * What was the SCM version (i.e. Git hash) for the BlackLab.jar used for indexing?
     * @return the SCM version
     */
    @Override
    public String indexBlackLabScmRevision() {
        String rev = versionInfo.blackLabScmRevision;
        return StringUtils.isEmpty(rev) ? "UNKNOWN" : rev;
    }

    // Methods that mutate data
    // ------------------------------------

    synchronized AnnotatedFieldImpl getOrCreateAnnotatedField(String name) {
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
        versionInfo.timeModified = TimeUtil.timestamp();
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
            annotation.createSensitivities(annotationWriter.getSensitivitySetting());
            //annotation.setOffsetsSensitivity(annotation.mainSensitivity().sensitivity());
            annotationWriter.setAnnotation(annotation);
        }
        String mainAnnotName = fieldWriter.mainAnnotation().name();
        cf.getOrCreateAnnotation(mainAnnotName); // create main annotation
        cf.setMainAnnotationName(mainAnnotName); // set main annotation
        fieldWriter.setAnnotatedField(cf);

        return cf;
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
        custom.put("displayName", displayName);
    }

    public void setDescription(String description) {
        ensureNotFrozen();
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
     * @deprecated use {@link #custom()} and set("textDirection", textDirection.getCode()) instead
     */
    @Override
    @Deprecated
    public void setTextDirection(TextDirection textDirection) {
        ensureNotFrozen();
        custom.put("textDirection", textDirection.getCode());
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
        String displayName = custom.get("displayName", "");
        return !StringUtils.isEmpty(displayName) ? displayName :
                StringUtils.capitalize(IndexMetadata.indexNameFromDirectory(index.indexDirectory()));
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

    protected void ensureMainAnnotatedFieldSet() {
        if (annotatedFields.main() != null)
            return; // we already know our main annotated field, probably from the metadata

        // "Detect" main contents field and main annotations of annotated fields
        AnnotatedFieldImpl mainAnnotatedField = null;
        for (AnnotatedField d: annotatedFields()) {
            if (mainAnnotatedField == null)
                mainAnnotatedField = (AnnotatedFieldImpl) d;
        }
        if (mainAnnotatedField != null)
            annotatedFields.setMainAnnotatedField(mainAnnotatedField);
    }

    @Override
    public synchronized long tokenCount() {
        ensureDocsAndTokensCounted();
        return tokenCount;
    }

    @Override
    public synchronized Map<String, Long> tokenCountPerField() {
        ensureDocsAndTokensCounted();
        return Collections.unmodifiableMap(tokenCountPerField);
    }

    @Override
    public synchronized int documentCount() {
        ensureDocsAndTokensCounted();
        return documentCount;
    }

    private synchronized void ensureDocsAndTokensCounted() {
        if (!tokenCountCalculated) {
            tokenCountCalculated = true;
            tokenCount = 0;
            tokenCountPerField.clear();
            if (!isNewIndex()) {
                // Count tokens for each field (and documents while we're at it)
                for (AnnotatedField field: annotatedFields()) {
                    documentCount = 0;
                    long[] fieldTokenCount = new long[] { 0 };
                    // Add up token counts for all the documents
                    Annotation annot = field.mainAnnotation();
                    AnnotationForwardIndex afi = index.forwardIndex(field).get(annot);
                    index.forEachDocument((__, docId) -> {
                        int docLength = afi.docLength(docId);
                        if (docLength >= 1) {
                            // Positive docLength means that this document has a value for this annotated field
                            // (e.g. the index metadata document does not and returns 0)
                            fieldTokenCount[0] += docLength - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
                            documentCount++; // NOTE: we dont use afi.size() because that includes deleted docs.
                        }
                    });
                    tokenCountPerField.put(field.name(), fieldTokenCount[0]);
                }
                tokenCount = tokenCountPerField.get(mainAnnotatedField().name());
            }
        }
    }

    @Override
    public void save() {
        if (!index.indexMode())
            throw new RuntimeException("Cannot save indexmetadata in search mode!");
        if (indexWriter == null)
            throw new RuntimeException("Cannot save indexmetadata, indexWriter == null");

        if (!isFrozen())
            ensureMainAnnotatedFieldSet();

        metadataDocument.saveToIndex(indexWriter, this);
    }

    @Override
    public void freezeBeforeIndexing() {
        // Contrary to the "classic" index format, with this one the metadata
        // cannot change while indexing. So freeze it now to enforce that.
        // UPDATE: we actually CAN update metadata while indexing and probably should
        //  (e.g. because you can add documents with different configs to one corpus)
        //freeze();
    }

    @Override
    public void addToTokenCount(long tokensProcessed) {
        // We don't keep the token count in the metadata in the integrated
        // index format because it cannot change during indexing.
        // However, we do want to keep track of it while creating or appending.
        // We just don't check that the metadata isn't frozen (we don't care what value gets written there)
        tokenCount += tokensProcessed;
    }

    @Override
    public synchronized MetadataField registerMetadataField(String fieldName) {
        MetadataFieldImpl f = metadataFields.register(fieldName);
        // We don't keep track of metadata field values in the integrated
        // index format because it cannot change during indexing.
        // Instead we will use DocValues to get the field values when necessary.
        f.setKeepTrackOfValues(false);
        return f;
    }

    /**
     * Is this a new, empty index?
     *
     * An empty index is one that doesn't have a main contents field yet.
     *
     * @return true if it is, false if not.
     */
    @Override
    public boolean isNewIndex() {
        // An empty index only contains the index metadata document
        return index.reader().numDocs() <= 1;
    }

    protected MetadataFieldValues.Factory createMetadataFieldValuesFactory() {
        return new MetadataFieldValuesFromIndex.Factory(index);
    }

    @Override
    public int metadataDocId() {
        return metadataDocument.getDocumentId();
    }

    @Override
    public CustomPropsMap custom() {
        return custom;
    }

    @Override
    public String getIndexMetadataAsString() {
        try {
            return metadataDocument.getMetadataJson(index.reader(), metadataDocId());
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void setIndexMetadataFromString(String metadata) {
        if (!index.indexMode())
            throw new RuntimeException("Cannot save indexmetadata in search mode!");
        metadataDocument.saveToIndex(indexWriter, metadata);
    }

    public BlackLabIndex.IndexType getIndexType() {
        return BlackLabIndex.IndexType.INTEGRATED;
    }
}
