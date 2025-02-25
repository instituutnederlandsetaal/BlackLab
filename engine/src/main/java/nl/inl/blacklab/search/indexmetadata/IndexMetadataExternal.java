package nl.inl.blacklab.search.indexmetadata;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;
import nl.inl.util.StringUtil;
import nl.inl.util.TimeUtil;

public class IndexMetadataExternal extends IndexMetadataAbstract {

    public static final String METADATA_FILE_NAME = "indexmetadata";

    private static final Charset INDEX_STRUCT_FILE_ENCODING = StandardCharsets.UTF_8;

    private static int maxMetadataValuesToStore = 50;

    /** When we save this file, should we write it as json or yaml? */
    private final boolean saveAsJson;

    /** Where to save indexmetadata.json */
    private final File indexDir;

    /**
     * Construct an IndexMetadata object, querying the index for the available
     * fields and their types.
     *
     * @param index the index of which we want to know the structure
     * @param indexDir where the index (and the metadata file) is stored
     * @param createNewIndex whether we're creating a new index
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     * @throws IndexVersionMismatch if the index is too old or too new to be opened by this BlackLab version
     */
    public IndexMetadataExternal(BlackLabIndex index, File indexDir, boolean createNewIndex,
            ConfigInputFormat config) throws IndexVersionMismatch {
        super(index);
        this.indexDir = indexDir;

        // Find existing metadata file, if any.
        File metadataFile = FileUtil.findFile(List.of(this.indexDir), IndexMetadataExternal.METADATA_FILE_NAME,
                Arrays.asList("json", "yaml", "yml"));

        saveAsJson = false;
        if (createNewIndex) {
            if (metadataFile != null) {
                // Don't leave the old metadata file if we're creating a new index
                if (metadataFile.exists() && !metadataFile.delete())
                    throw new BlackLabRuntimeException("Could not delete file: " + metadataFile);
            }

            // Always write a .yaml file for new index
            metadataFile = new File(this.indexDir, IndexMetadataExternal.METADATA_FILE_NAME + ".yaml");
        }

        if (createNewIndex && config != null) {
            // Create an index metadata file from this config.
            ObjectNode jsonRoot = createIndexMetadataFromConfig(config);
            decodeFromJson(jsonRoot, null, true);
            save();
        } else {
            // Read existing metadata or create empty new one
            readOrCreateMetadata(this.index.reader(), createNewIndex, metadataFile, false);
        }
    }

    /**
     * Construct an IndexMetadata object, querying the index for the available
     * fields and their types.
     *
     * @param index the index of which we want to know the structure
     * @param indexDir where the index (and the metadata file) is stored
     * @param createNewIndex whether we're creating a new index
     * @param indexTemplateFile JSON file to use as template for index structure /
     *            metadata (if creating new index)
     * @throws IndexVersionMismatch if the index is too old or too new to be opened by this BlackLab version
     */
    public IndexMetadataExternal(BlackLabIndex index, File indexDir, boolean createNewIndex, File indexTemplateFile)
            throws IndexVersionMismatch {
        super(index);
        this.indexDir = indexDir;
        // Find existing metadata file, if any.
        File metadataFile = FileUtil.findFile(List.of(this.indexDir), IndexMetadataExternal.METADATA_FILE_NAME,
                Arrays.asList("yaml", "yml", "json"));
        if (metadataFile != null && createNewIndex) {
            // Don't leave the old metadata file if we're creating a new index
            if (!metadataFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + metadataFile);
        }

        // If none found, or creating new index: metadata file should be same format as template.
        if (createNewIndex || metadataFile == null) {
            // No metadata file yet, or creating a new index;
            // use same metadata format as the template
            boolean templateIsJson = indexTemplateFile != null && indexTemplateFile.getName().endsWith(".json");
            String templateExt = templateIsJson ? "json" : "yaml";
            if (createNewIndex && metadataFile != null) {
                // We're creating a new index, but also found a previous metadata file.
                // Is it a different format than the template? If so, we would end up
                // with two metadata files, which is confusing and might lead to errors.
                boolean existingIsJson = metadataFile.getName().endsWith(".json");
                if (existingIsJson != templateIsJson) {
                    // Delete the existing, different-format file to avoid confusion.
                    if (!metadataFile.delete())
                        throw new BlackLabRuntimeException("Could not delete file: " + metadataFile);
                }
            }
            metadataFile = new File(this.indexDir, IndexMetadataExternal.METADATA_FILE_NAME + "." + templateExt);
        }
        saveAsJson = metadataFile.getName().endsWith(".json");
        boolean usedTemplate = false;
        if (createNewIndex && indexTemplateFile != null) {
            // Copy the template file to the index dir and read the metadata again.
            try {
                String fileContents = FileUtils.readFileToString(indexTemplateFile, INDEX_STRUCT_FILE_ENCODING);
                FileUtils.write(metadataFile, fileContents, INDEX_STRUCT_FILE_ENCODING);
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
            usedTemplate = true;
        }

        readOrCreateMetadata(this.index.reader(), createNewIndex, metadataFile, usedTemplate);
    }

    public static void setMaxMetadataValuesToStore(int n) {
        maxMetadataValuesToStore = n;
    }

    public static int maxMetadataValuesToStore() {
        return maxMetadataValuesToStore;
    }

    private void readOrCreateMetadata(IndexReader reader, boolean createNewIndex, File metadataFile,
            boolean usedTemplate) throws IndexVersionMismatch {
        ensureNotFrozen();

        // Read and interpret index metadata file
        ObjectNode jsonRoot;
        if ((createNewIndex && !usedTemplate) || !metadataFile.exists()) {
            // No metadata file yet; start with a blank one
            jsonRoot = createEmptyIndexMetadata();
            usedTemplate = false;
        } else {
            // Read the metadata file
            jsonRoot = readMetadataFile(metadataFile);
        }
        decodeFromJson(jsonRoot, reader, usedTemplate);
        if (!createNewIndex) { // new index doesn't have this information yet
            detectMainAnnotation(reader);
        }
    }

    private ObjectNode readMetadataFile(File metadataFile) {
        ObjectNode jsonRoot;
        try {
            boolean isJson = metadataFile.getName().endsWith(".json");
            ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
            jsonRoot = (ObjectNode) mapper.readTree(metadataFile);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        return jsonRoot;
    }

    @Override
    public void save() {
        String ext = saveAsJson ? ".json" : ".yaml";
        File metadataFile = new File(indexDir, IndexMetadataExternal.METADATA_FILE_NAME + ext);
        try {
            boolean isJson = metadataFile.getName().endsWith(".json");
            ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
            mapper.writeValue(metadataFile, encodeToJson());
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public String indexFlag(String name) {
        // External index doesn't support index flags, always returns empty (not set)
        return "";
    }

    @Override
    protected String getLatestIndexFormat() {
        /* The latest index format. Written to the index metadata file.
         *
         * - 3: first version to include index metadata file
         * - 3.1: tag length in payload
         * - 4: integrated index format (see IndexMetadataIntegrated)
         */
        return "3.1";
    }

    @Override
    protected MetadataFieldValues.Factory createMetadataFieldValuesFactory() {
        return new MetadataFieldValuesInMetadataFile.Factory();
    }

    @Override
    public int documentCount() {
        return index.reader().numDocs(); // not strictly correct; includes deleted docs
    }

    @Override
    public BlackLabIndex.IndexType getIndexType() {
        return BlackLabIndex.IndexType.EXTERNAL_FILES;
    }

    /**
     * Encode the index structure to an (in-memory) JSON structure.
     *
     * @return json structure
     */
    public ObjectNode encodeToJson() {
        ObjectMapper mapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = mapper.createObjectNode();
        jsonRoot.put("displayName", displayName());
        jsonRoot.put("description", description());
        jsonRoot.put("contentViewable", contentViewable());
        jsonRoot.put("textDirection", textDirection().getCode());
        jsonRoot.put("documentFormat", documentFormat());
        jsonRoot.put("tokenCount", tokenCount());
        ObjectNode versionInfo = jsonRoot.putObject("versionInfo");
        versionInfo.put("blackLabBuildTime", indexBlackLabBuildTime());
        versionInfo.put("blackLabVersion", indexBlackLabVersion());
        versionInfo.put("indexFormat", indexFormat());
        versionInfo.put("timeCreated", timeCreated());
        versionInfo.put("timeModified", timeModified());
        // Indicates that we always index words+1 tokens (last token is
        // for XML tags after the last word)
        versionInfo.put("alwaysAddClosingToken", true);
        // Indicates that start tag annotation payload contains tag lengths,
        // and there is no end tag annotation
        versionInfo.put("tagLengthInPayload", true);

        ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
        fieldInfo.put("namingScheme", "DEFAULT");
        fieldInfo.put("defaultAnalyzer", metadataFields.defaultAnalyzerName());
        fieldInfo.put("unknownCondition", metadataFields.defaultUnknownCondition());
        fieldInfo.put("unknownValue", metadataFields.defaultUnknownValue());
        if (custom().containsKey("titleField"))
            fieldInfo.put("titleField", custom().get("titleField", ""));
        if (custom().containsKey("authorField"))
            fieldInfo.put("authorField", custom().get("authorField", ""));
        if (custom().containsKey("dateField"))
            fieldInfo.put("dateField", custom().get("dateField", ""));
        if (metadataFields.pidField() != null)
            fieldInfo.put(MetadataFields.SPECIAL_FIELD_SETTING_PID, metadataFields.pidField().name());
        ArrayNode metadataFieldGroups = fieldInfo.putArray("metadataFieldGroups");
        ObjectNode annotationGroups = fieldInfo.putObject("annotationGroups");
        ObjectNode metadataFields = fieldInfo.putObject("metadataFields");
        ObjectNode jsonAnnotatedFields = fieldInfo.putObject("complexFields");

        // Add metadata field group info
        for (MetadataFieldGroup g: metadataFields().groups().values()) {
            ObjectNode group = metadataFieldGroups.addObject();
            group.put("name", g.name());
            if (g.addRemainingFields())
                group.put("addRemainingFields", true);
            ArrayNode arr = group.putArray("fields");
            Json.arrayOfStrings(arr, g.stream().collect(Collectors.toList()));
        }
        // Add annotation group info
        for (AnnotatedField f: annotatedFields()) {
            AnnotationGroups groups = annotatedFields().annotationGroups(f.name());
            if (groups != null) {
                ArrayNode jsonGroups = annotationGroups.putArray(f.name());
                for (AnnotationGroup g: groups) {
                    ObjectNode jsonGroup = jsonGroups.addObject();
                    jsonGroup.put("name", g.groupName());
                    if (g.addRemainingAnnotations())
                        jsonGroup.put("addRemainingAnnotations", true);
                    ArrayNode arr = jsonGroup.putArray("annotations");
                    Json.arrayOfStrings(arr, g.annotations());
                }
            }
        }

        // Add metadata field info
        for (MetadataField f: this.metadataFields) {
            UnknownCondition unknownCondition = f.unknownCondition();
            ObjectNode fi = metadataFields.putObject(f.name());
            fi.put("displayName", f.displayName());
            fi.put("uiType", f.uiType());
            fi.put("description", f.description());
            fi.put("type", f.type().stringValue());
            fi.put("analyzer", f.analyzerName());
            fi.put("unknownValue", f.unknownValue());
            fi.put("unknownCondition", unknownCondition.toString());
            TruncatableFreqList metaFieldValues = f.values(maxMetadataValuesToStore()).valueList();
            fi.put("valueListComplete", !metaFieldValues.isTruncated());
            Map<String, Long> values = metaFieldValues.getValues();
            if (values != null) {
                ObjectNode jsonValues = fi.putObject("values");
                for (Map.Entry<String, Long> e: values.entrySet()) {
                    jsonValues.put(e.getKey(), e.getValue());
                }
            }
            Map<String, String> displayValues = f.custom().get("displayValues", Collections.emptyMap());
            if (displayValues != null) {
                ObjectNode jsonDisplayValues = fi.putObject("displayValues");
                for (Map.Entry<String, String> e: displayValues.entrySet()) {
                    jsonDisplayValues.put(e.getKey(), e.getValue());
                }
            }
            List<String> displayOrder = f.displayOrder();
            if (displayOrder != null) {
                ArrayNode jsonDisplayValues = fi.putArray("displayOrder");
                for (String value: displayOrder) {
                    jsonDisplayValues.add(value);
                }
            }
        }

        // Add annotated field info
        for (AnnotatedField f: annotatedFields) {
            ObjectNode fieldInfo2 = jsonAnnotatedFields.putObject(f.name());
            fieldInfo2.put("displayName", f.displayName());
            fieldInfo2.put("description", f.description());
            if (f.mainAnnotation() != null)
                fieldInfo2.put("mainProperty", f.mainAnnotation().name());
            ArrayNode arr = fieldInfo2.putArray("displayOrder");
            Json.arrayOfStrings(arr, ((AnnotatedFieldImpl) f).custom().get("displayOrder", Collections.emptyList()));
            ArrayNode annots = fieldInfo2.putArray("annotations");
            for (Annotation annotation: f.annotations()) {
                ObjectNode annot = annots.addObject();
                annot.put("name", annotation.name());
                annot.put("displayName", annotation.displayName());
                annot.put("description", annotation.description());
                annot.put("uiType", annotation.uiType());
                annot.put("isInternal", annotation.isInternal());
                if (annotation.subannotationNames().size() > 0) {
                    ArrayNode subannots = annot.putArray("subannotations");
                    for (String subannotName: annotation.subannotationNames()) {
                        subannots.add(subannotName);
                    }
                }
            }
        }

        return jsonRoot;

    }

    /**
     * Extract the index structure from the (in-memory) JSON structure (and Lucene
     * index).
     *
     * Looks at the Lucene index to detect certain information (sometimes) missing
     * from the JSON structure, such as naming scheme and available annotations and
     * alternatives for annotated fields. (Should probably eventually all be recorded
     * in the metadata.)
     *
     * @param jsonRoot JSON structure to extract
     * @param reader index reader used to detect certain information, or null if we
     *            don't have an index reader (e.g. because we're creating a new
     *            index)
     * @param usedTemplate whether the JSON structure was read from a indextemplate
     *            file. If so, clear certain parts of it that aren't relevant
     *            anymore.
     * @throws IndexVersionMismatch if the index is too old or too new to open with this BlackLab version
     */
    private void decodeFromJson(ObjectNode jsonRoot, IndexReader reader, boolean usedTemplate) throws IndexVersionMismatch {
        ensureNotFrozen();

        // Read and interpret index metadata file
        warnUnknownKeys("at top-level", jsonRoot, KEYS_TOP_LEVEL);
        setDisplayName(Json.getString(jsonRoot, "displayName", ""));
        setDescription(Json.getString(jsonRoot, "description", ""));
        setContentViewable(Json.getBoolean(jsonRoot, "contentViewable", false));
        custom().put("textDirection", Json.getString(jsonRoot, "textDirection", "ltr"));
        setDocumentFormat(Json.getString(jsonRoot, "documentFormat", ""));
        tokenCount = Json.getLong(jsonRoot, "tokenCount", 0);

        ObjectNode versionInfo = Json.getObject(jsonRoot, "versionInfo");
        warnUnknownKeys("in versionInfo", versionInfo, KEYS_VERSION_INFO);
        indexFormat = Json.getString(versionInfo, "indexFormat", "");
        blackLabBuildTime = Json.getString(versionInfo, "blackLabBuildTime", "UNKNOWN");
        blackLabVersion = Json.getString(versionInfo, "blackLabVersion", "UNKNOWN");
        timeCreated = Json.getString(versionInfo, "timeCreated", "");
        timeModified = Json.getString(versionInfo, "timeModified", timeCreated);
        boolean alwaysHasClosingToken = Json.getBoolean(versionInfo, "alwaysAddClosingToken", false);
        if (!alwaysHasClosingToken)
            throw new IndexVersionMismatch(
                    "Your index is too old (alwaysAddClosingToken == false). Please use v1.7.1 or re-index your data.");
        boolean tagLengthInPayload = Json.getBoolean(versionInfo, "tagLengthInPayload", false);
        if (!tagLengthInPayload) {
            logger.warn(
                    "Your index is too old (tagLengthInPayload == false). Searches using XML elements like <s> may not work correctly. If this is a problem, please use v1.7.1 or re-index your data.");
            //throw new IndexVersionMismatch("Your index is too old (tagLengthInPayload == false). Please use v1.7.1 or re-index your data.");
        }

        // Specified in index metadata file?
        ObjectNode fieldInfo = Json.getObject(jsonRoot, "fieldInfo");
        warnUnknownKeys("in fieldInfo", fieldInfo, KEYS_FIELD_INFO);
        metadataFields.setDefaultUnknownCondition(Json.getString(fieldInfo, "unknownCondition",
                UnknownCondition.NEVER.stringValue()));
        metadataFields.setDefaultUnknownValue(Json.getString(fieldInfo, "unknownValue", "unknown"));

        ObjectNode metaFieldConfigs = Json.getObject(fieldInfo, "metadataFields");
        boolean hasMetaFields = metaFieldConfigs.size() > 0;
        ObjectNode annotatedFieldConfigs = Json.getObject(fieldInfo, "complexFields");
        boolean hasAnnotatedFields = annotatedFieldConfigs.size() > 0;
        boolean hasFieldInfo = hasMetaFields || hasAnnotatedFields;

        if (hasFieldInfo && fieldInfo.has("annotationGroups")) {
            annotatedFields.clearAnnotationGroups();
            JsonNode groupingsPerField = fieldInfo.get("annotationGroups");
            Iterator<Map.Entry<String, JsonNode>> it = groupingsPerField.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode groups = entry.getValue();
                List<AnnotationGroup> annotationGroups = new ArrayList<>();
                for (int i = 0; i < groups.size(); i++) {
                    JsonNode group = groups.get(i);
                    warnUnknownKeys("in annotation group", group, KEYS_ANNOTATION_GROUP);
                    String groupName = Json.getString(group, "name", "UNKNOWN");
                    List<String> annotations = Json.getListOfStrings(group, "annotations");
                    boolean addRemainingAnnotations = Json.getBoolean(group, "addRemainingAnnotations", false);
                    annotationGroups.add(new AnnotationGroup(fieldName, groupName, annotations,
                            addRemainingAnnotations));
                }
                annotatedFields.putAnnotationGroups(fieldName, new AnnotationGroups(fieldName, annotationGroups));
            }
        }
        if (hasFieldInfo && fieldInfo.has("metadataFieldGroups")) {
            Map<String, MetadataFieldGroupImpl> groupMap = new LinkedHashMap<>();
            JsonNode groups = fieldInfo.get("metadataFieldGroups");
            for (int i = 0; i < groups.size(); i++) {
                JsonNode group = groups.get(i);
                warnUnknownKeys("in metadataFieldGroup", group, KEYS_METADATA_GROUP);
                String name = Json.getString(group, "name", "UNKNOWN");
                List<String> fields = Json.getListOfStrings(group, "fields");
                for (String f: fields) {
                    // Ensure field exists
                    metadataFields().register(f);
                }
                boolean addRemainingFields = Json.getBoolean(group, "addRemainingFields", false);
                MetadataFieldGroupImpl metadataGroup = new MetadataFieldGroupImpl(name, fields,
                        addRemainingFields);
                groupMap.put(name, metadataGroup);
            }
            metadataFields.setMetadataGroups(groupMap);
        }
        if (hasFieldInfo) {
            // Metadata fields
            Iterator<Map.Entry<String, JsonNode>> it = metaFieldConfigs.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode fieldConfig = entry.getValue();
                warnUnknownKeys("in metadata field config for '" + fieldName + "'", fieldConfig,
                        KEYS_META_FIELD_CONFIG);
                FieldType fieldType = FieldType.fromStringValue(Json.getString(fieldConfig, "type", "tokenized"));
                MetadataFieldImpl fieldDesc = new MetadataFieldImpl(fieldName, fieldType, metadataFields.getMetadataFieldValuesFactory());
                fieldDesc.setDisplayName(Json.getString(fieldConfig, "displayName", fieldName));
                fieldDesc.putCustom("uiType", Json.getString(fieldConfig, "uiType", ""));
                fieldDesc.setDescription(Json.getString(fieldConfig, "description", ""));
                //fieldDesc.setGroup(Json.getString(fieldConfig, "group", ""));
                fieldDesc.setAnalyzer(Json.getString(fieldConfig, "analyzer", "DEFAULT"));
                fieldDesc.putCustom("unknownValue",
                        Json.getString(fieldConfig, "unknownValue", metadataFields.defaultUnknownValue()));
                UnknownCondition unk = UnknownCondition
                        .fromStringValue(Json.getString(fieldConfig, "unknownCondition",
                                metadataFields.defaultUnknownCondition()));
                fieldDesc.putCustom("unknownCondition", unk.stringValue());
                if (fieldConfig.has("values"))
                    fieldDesc.setValues(fieldConfig.get("values"));
                if (fieldConfig.has("displayValues"))
                    fieldDesc.setDisplayValues(fieldConfig.get("displayValues"));
                if (fieldConfig.has("displayOrder"))
                    fieldDesc.putCustom("displayOrder", Json.getListOfStrings(fieldConfig, "displayOrder"));
                MetadataFieldValues values = fieldDesc.values(maxMetadataValuesToStore());
                if (fieldConfig.has("valueListComplete") && !values.valueList().isTruncated()) {
                    fieldDesc.setValueListComplete(Json.getBoolean(fieldConfig, "valueListComplete", false));
                }
                metadataFields.put(fieldName, fieldDesc);
            }

            // Annotated fields
            it = annotatedFieldConfigs.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode fieldConfig = entry.getValue();
                warnUnknownKeys("in annotated field config for '" + fieldName + "'", fieldConfig,
                        KEYS_ANNOTATED_FIELD_CONFIG);
                AnnotatedFieldImpl fieldDesc = new AnnotatedFieldImpl(fieldName);
                fieldDesc.setDisplayName(Json.getString(fieldConfig, "displayName", fieldName));
                fieldDesc.setDescription(Json.getString(fieldConfig, "description", ""));
                String mainAnnotationName = Json.getString(fieldConfig, "mainProperty", "");
                if (mainAnnotationName.length() > 0)
                    fieldDesc.setMainAnnotationName(mainAnnotationName);

                // Process information about annotations (displayName, uiType, etc.
                ArrayList<String> annotationOrder = new ArrayList<>();
                if (fieldConfig.has("annotations")) {
                    JsonNode annotations = fieldConfig.get("annotations");
                    Iterator<JsonNode> itAnnot = annotations.elements();
                    while (itAnnot.hasNext()) {
                        JsonNode jsonAnnotation = itAnnot.next();
                        Iterator<Map.Entry<String, JsonNode>> itAnnotOpt = jsonAnnotation.fields();
                        AnnotationImpl annotation = new AnnotationImpl(fieldDesc);
                        while (itAnnotOpt.hasNext()) {
                            Map.Entry<String, JsonNode> opt = itAnnotOpt.next();
                            switch (opt.getKey()) {
                            case "name":
                                annotation.setName(opt.getValue().textValue());
                                annotationOrder.add(opt.getValue().textValue());
                                break;
                            case "displayName":
                                annotation.setDisplayName(opt.getValue().textValue());
                                break;
                            case "description":
                                annotation.setDescription(opt.getValue().textValue());
                                break;
                            case "uiType":
                                annotation.custom().put("uiType", opt.getValue().textValue());
                                break;
                            case "isInternal":
                                if (opt.getValue().booleanValue())
                                    annotation.setInternal();
                                break;
                            case "subannotations":
                                annotation.setSubannotationNames(Json.getListOfStrings(jsonAnnotation, "subannotations"));
                                break;
                            case "hasForwardIndex":
                                // (used to be detected, now stored in metadata)
                                boolean hasForwardIndex = opt.getValue().booleanValue();
                                annotation.setForwardIndex(hasForwardIndex);
                                break;
                            default:
                                logger.warn("Unknown key " + opt.getKey() + " in annotation for field '" + fieldName
                                        + "' in indexmetadata file");
                                break;
                            }
                        }
                        if (StringUtils.isEmpty(annotation.name()))
                            logger.warn("Annotation entry without name for field '" + fieldName
                                    + "' in indexmetadata file; skipping");
                        else
                            fieldDesc.putAnnotation(annotation);
                    }
                }

                // These annotations should get no forward index (deprecated setting)
                JsonNode nodeNoForwardIndexAnnotations = fieldConfig.get("noForwardIndexProps");
                if (nodeNoForwardIndexAnnotations instanceof ArrayNode) {
                    Iterator<JsonNode> itNFIP = nodeNoForwardIndexAnnotations.elements();
                    Set<String> noForwardIndex = new HashSet<>();
                    while (itNFIP.hasNext()) {
                        noForwardIndex.add(itNFIP.next().asText());
                    }
                    fieldDesc.setNoForwardIndexAnnotations(noForwardIndex);
                } else {
                    String noForwardIndex = Json.getString(fieldConfig, "noForwardIndexProps", "").trim();
                    if (noForwardIndex.length() > 0) {
                        String[] noForwardIndexAnnotations = noForwardIndex.split(StringUtil.REGEX_WHITESPACE);
                        fieldDesc.setNoForwardIndexAnnotations(new HashSet<>(Arrays.asList(noForwardIndexAnnotations)));
                    }
                }

                // This is the "natural order" of our annotations
                // (probably not needed anymore - if not specified, the order of the annotations
                // will be used)
                List<String> displayOrder = Json.getListOfStrings(fieldConfig, "displayOrder");
                if (displayOrder.isEmpty()) {
                    displayOrder.addAll(annotationOrder);
                }
                fieldDesc.custom.put("displayOrder", displayOrder);

                annotatedFields.put(fieldName, fieldDesc);
            }
        }
        FieldInfos fis = reader == null ? null : FieldInfos.getMergedFieldInfos(reader);
        if (fis != null) {
            // Detect fields
            for (FieldInfo fi: fis) {
            //for (int i = 0; i < fis.size(); i++) {
                //FieldInfo fi = fis.fieldInfo(i);
                String name = fi.name;

                // Parse the name to see if it is a metadata field or part of an annotated field.
                String[] parts;
                if (name.endsWith("Numeric")) {
                    // Special case: this is not a annotation alternative, but a numeric
                    // alternative for a metadata field.
                    // (TODO: this should probably be changed or removed; improve numeric field support in general)
                    parts = new String[] { name };
                } else {
                    parts = AnnotatedFieldNameUtil.getNameComponents(name);
                }
                if (parts.length == 1 && !annotatedFields.exists(parts[0])) {
                    if (!metadataFields.exists(name)) {
                        // Metadata field, not found in metadata JSON file
                        FieldType type = getFieldType(name);
                        MetadataFieldImpl metadataFieldDesc = new MetadataFieldImpl(name, type, metadataFields.getMetadataFieldValuesFactory());
                        metadataFieldDesc.putCustom("unknownCondition", metadataFields.defaultUnknownCondition());
                        metadataFieldDesc.putCustom("unknownValue", metadataFields.defaultUnknownValue());
                        //metadataFieldDesc.setDocValuesType(fi.getDocValuesType());
                        metadataFields.put(name, metadataFieldDesc);
                    }
                } else {
                    // Part of annotated field.
                    if (metadataFields.exists(parts[0])) {
                        throw new BlackLabRuntimeException(
                                "Annotated field and metadata field with same name, error! ("
                                        + parts[0] + ")");
                    }

                    // Get or create descriptor object.
                    AnnotatedFieldImpl cfd = getOrCreateAnnotatedField(parts[0]);
                    cfd.processIndexField(parts, fi);
                }
            } // even if we have metadata, we still have to detect annotations/sensitivities
        }

        // Link subannotations to their parent annotation
        for (AnnotatedField f: annotatedFields) {
            Annotations annot = f.annotations();
            for (Annotation a: annot) {
                if (a.subannotationNames().size() > 0) {
                    // Link these subannotations back to their parent annotation
                    for (String name: a.subannotationNames()) {
                        annot.get(name).setSubAnnotation(a);
                    }
                }
            }
        }

        metadataFields.setDefaultAnalyzer(Json.getString(fieldInfo, "defaultAnalyzer", "DEFAULT"));

        metadataFields.setTopLevelCustom(custom());
        metadataFields.clearSpecialFields();
        if (fieldInfo.has(MetadataFields.SPECIAL_FIELD_SETTING_PID))
            metadataFields.setPidField(fieldInfo.get(MetadataFields.SPECIAL_FIELD_SETTING_PID).textValue());
        if (fieldInfo.has("authorField"))
            custom.put("authorField", fieldInfo.get("authorField").textValue());
        if (fieldInfo.has("dateField"))
            custom.put("dateField", fieldInfo.get("dateField").textValue());
        if (fieldInfo.has("titleField"))
            custom.put("titleField", fieldInfo.get("titleField").textValue());
        if (custom.get("titleField", "").isEmpty()) {
            if (metadataFields.pidField() != null)
                custom.put("titleField", metadataFields.pidField().name());
            else
                custom.put("titleField", "fromInputFile");
            logger.warn("No titleField specified; using default " + custom.get("titleField") + ". In future versions, no default will be chosen.");
        }

        if (usedTemplate) {
            // Update / clear possible old values that were in the template file
            // (template file may simply be the metadata file copied from a previous
            // version)

            // Reset version info
            blackLabBuildTime = BlackLab.buildTime();
            blackLabVersion = BlackLab.version();
            indexFormat = getLatestIndexFormat();
            timeModified = timeCreated = TimeUtil.timestamp();

            // Clear any recorded values in metadata fields
            metadataFields.resetForIndexing();
        }
    }
}
