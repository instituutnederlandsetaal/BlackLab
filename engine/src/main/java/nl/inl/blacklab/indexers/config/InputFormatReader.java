package nl.inl.blacklab.indexers.config;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.indexers.config.ConfigInputFormat.FileType;
import nl.inl.blacklab.indexers.config.ConfigLinkedDocument.MissingLinkPathAction;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;

/**
 * Reads ConfigInputFormat from a YAML or JSON source.
 */
public class InputFormatReader extends YamlJsonReader {

    private static final Logger logger = LogManager.getLogger(InputFormatReader.class);

    public static final String KEY_PROCESSOR = "processor";

    /**
     * Reads a config from a YAML or JSON file.
     * @throws InvalidInputFormatConfig if the file is not a valid config
     */
    public static void read(Reader r, boolean isJson, ConfigInputFormat cfg) throws IOException {
        ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();

        JsonNode root;
        try {
            root = mapper.readTree(r);
        } catch (JsonParseException e) {
            throw new InvalidInputFormatConfig("Could not parse config " + cfg.getName() + ": " + e.getMessage());
        }
        InputFormatReader ifr = new InputFormatReader(cfg);
        ifr.read(root);
    }

    /**
     * Reads a config from a YAML or JSON file.
     * @throws InvalidInputFormatConfig if the file is not a valid config
     */
    public static void read(File file, ConfigInputFormat cfg)
            throws IOException {
        read(FileUtil.openForReading(file), file.getName().endsWith(".json"), cfg);
        cfg.setReadFromFile(file);
    }

    /** The input format we're reading. */
    private final ConfigInputFormat cfg;

    private InputFormatReader(ConfigInputFormat cfg) {
        this.cfg = cfg;
    }

    /** Describe the format we're reading, for e.g. error messages. */
    private String descFormat() {
        File f = cfg.getReadFromFile();
        if (f != null) {
            return f.getPath();
        }
        return StringUtils.defaultString(cfg.getName(), "(unnamed)");
    }

    /** Describe the format we're reading, for e.g. error messages. */
    private String inFormat() {
        File f = cfg.getReadFromFile();
        if (f != null) {
            return " in format file " + f.getPath();
        }
        return " in format " + StringUtils.defaultString(cfg.getName(), "(unnamed)");
    }

    private void read(JsonNode root) {
        obj(root, "root node");
        Iterator<Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "version":
                cfg.setVersion(integer(e));
                logger.warn("Found version key in .blf.yaml file. This is ignored; it is better to remove it.");
                break;
            case "displayName":
                cfg.setDisplayName(str(e));
                break;
            case "description":
                cfg.setDescription(str(e));
                break;
            case "helpUrl":
                cfg.setHelpUrl(str(e));
                break;
            case "baseFormat": {
                throw new InvalidInputFormatConfig("Input format configuration inheritance (baseFormat key) was removed. " +
                        "Please copy the base format configuration to your own format and customize it.");
            }
            case "type":
                cfg.setType(str(e));
                break;
            case KEY_PROCESSOR:
                // (we allow this at the top-level now because it's something most users will want to do while VTD
                //  remains the default)
                // Set the (XML) file processor to use, VTD or Saxon
                cfg.setFileType(FileType.XML); // processor only supported for XML files right now
                cfg.addFileTypeOption(DocIndexerXPath.FT_OPT_PROCESSOR, str(e));
                break;
            case "fileType":
                cfg.setFileType(FileType.fromStringValue(str(e)));
                break;
            case "fileTypeOptions":
                readFileTypeOptions(e, cfg);
                break;
            case "corpusConfig":
                readCorpusConfig(e, cfg.getCorpusConfig());
                break;
            case "namespaces":
                readStringMap(e, cfg.namespaces);
                break;
            case "documentPath":
                cfg.setDocumentPath(str(e));
                break;
            case "store":
                cfg.setStore(bool(e));
                break;
            case "indexFieldAs":
                readStringMap(e, cfg.indexFieldAs);
                break;
            case "annotatedFields":
                readAnnotatedFields(e, cfg);
                break;
            case "metadataDefaultAnalyzer":
                cfg.setMetadataDefaultAnalyzer(str(e));
                break;
            case "metadataDefaultUnknownCondition":
                cfg.setMetadataDefaultUnknownCondition(UnknownCondition.fromStringValue(str(e)));
                break;
            case "metadataDefaultUnknownValue":
                cfg.setMetadataDefaultUnknownValue(str(e));
                break;
            case "metadata":
                readMetadata(e, cfg);
                break;
            case "linkedDocuments":
                readLinkedDocuments(e, cfg);
                break;
            case "convertPlugin":
                cfg.setConvertPluginId(str(e));
                break;
            case "tagPlugin":
                cfg.setTagPluginId(str(e));
                break;
            case "isVisible":
                cfg.setVisible(bool(e));
                break;
            default:
                throw new InvalidInputFormatConfig("Unknown top-level key " + e.getKey() + inFormat());
            }
        }

        finalizeConfig();
    }

    private void finalizeConfig() {
        // Ensure that if we have any linked documents we want to store (like metadata), there exists an
        // annotated field where we can store it (even if it has no annotations).
        for (ConfigLinkedDocument ld: cfg.getLinkedDocuments().values()) {
            if (ld.shouldStore() && cfg.getAnnotatedField(ld.getName()) == null) {
                // Field doesn't exit yet. Create a dummy field for it.
                cfg.addAnnotatedField(ConfigAnnotatedField.createDummyForStoringLinkedDocument(ld.getName()));
            }
        }

        // Default to Saxon if no processor specified
        if (cfg.getFileType() == FileType.XML &&
                cfg.getFileTypeOptions().get(DocIndexerXPath.FT_OPT_PROCESSOR) == null) {
            cfg.addFileTypeOption(DocIndexerXPath.FT_OPT_PROCESSOR, DocIndexerSaxon.PROCESSOR_NAME);
        }
    }

    private void readCorpusConfig(Entry<String, JsonNode> ccEntry, ConfigCorpus corpusConfig) {
        ObjectNode node = obj(ccEntry.getValue(), "");
        Iterator<Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "displayName":
                corpusConfig.setDisplayName(str(e));
                break;
            case "description":
                corpusConfig.setDescription(str(e));
                break;
            case "contentViewable":
                corpusConfig.setContentViewable(bool(e));
                break;
            case "textDirection":
                corpusConfig.setTextDirection(TextDirection.fromCode(str(e)));
                break;
            case "specialFields":
                readStringMap(e, corpusConfig.specialFields);
                break;
            case "metadataFieldGroups":
                readMetadataFieldGroups(e, corpusConfig);
                break;
            case "annotationGroups":
                readAllAnnotationGroups(e, corpusConfig);
                break;
            default:
                throw new InvalidInputFormatConfig("Unknown key " + e.getKey() + " in corpusConfig" + inFormat());
            }
        }

    }

    private void readFileTypeOptions(Entry<String, JsonNode> ftOptEntry, ConfigInputFormat cfg) {
        ObjectNode node = obj(ftOptEntry);
        Iterator<Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            cfg.addFileTypeOption(e.getKey(), str(e));
        }
    }

    private Object stringOrStringMap(Entry<String, JsonNode> strMapEntry) {
        if (strMapEntry.getValue().isObject()) {
            Map<String, String> map = new HashMap<>();
            readStringMap(strMapEntry, map);
            return map;
        } else {
            return str(strMapEntry);
        }
    }

    private void readStringMap(Entry<String, JsonNode> strMapEntry, Map<String, String> addToMap) {
        ObjectNode node = obj(strMapEntry.getValue(), null);
        Iterator<Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            addToMap.put(e.getKey(), str(e));
        }
    }

    private void readStringList(Entry<String, JsonNode> strListEntry, List<String> addToList) {
        Iterator<JsonNode> it = array(strListEntry).elements();
        while (it.hasNext())
            addToList.add(str(it.next(), strListEntry.getKey() + " element"));
    }

    private void readMetadataFieldGroups(Entry<String, JsonNode> mfgEntry, ConfigCorpus cfg) {
        Iterator<JsonNode> itGroups = array(mfgEntry).elements();
        while (itGroups.hasNext()) {
            JsonNode group = itGroups.next();
            Iterator<Entry<String, JsonNode>> itGroup = obj(group, "metadata field group").fields();
            ConfigMetadataFieldGroup g = new ConfigMetadataFieldGroup();
            List<String> fields = new ArrayList<>();
            while (itGroup.hasNext()) {
                Entry<String, JsonNode> e = itGroup.next();
                switch (e.getKey()) {
                case "name":
                    g.setName(str(e));
                    break;
                case "fields":
                    readStringList(e, fields);
                    g.addFields(fields);
                    break;
                case "addRemainingFields":
                    g.setAddRemainingFields(bool(e));
                    break;
                default:
                    throw new InvalidInputFormatConfig(
                            "Unknown key " + e.getKey() + " in metadata field group " + g.getName() + inFormat());
                }
            }
            cfg.addMetadataFieldGroup(g);
        }
    }

    private void readAllAnnotationGroups(Entry<String, JsonNode> afagEntry, ConfigCorpus cfg) {
        Iterator<Entry<String, JsonNode>> itFields = obj(afagEntry.getValue(), "annotated fields annotation groupings").fields();
        while (itFields.hasNext()) {
            Entry<String, JsonNode> entry = itFields.next();
            ConfigAnnotationGroups g = new ConfigAnnotationGroups(entry.getKey());
            readAnnotationGroups(entry, g);
            cfg.addAnnotationGroups(g);
        }
    }

    private void readAnnotationGroups(Entry<String, JsonNode> entry, ConfigAnnotationGroups cfg) {
        Iterator<JsonNode> itGroups = array(entry.getValue(), "annotated field annotation groups").elements();
        while (itGroups.hasNext()) {
            JsonNode group = itGroups.next();
            Iterator<Entry<String, JsonNode>> itGroup = obj(group, "annotated field annotation group").fields();
            ConfigAnnotationGroup g = new ConfigAnnotationGroup();
            List<String> fields = new ArrayList<>();
            while (itGroup.hasNext()) {
                Entry<String, JsonNode> e = itGroup.next();
                switch (e.getKey()) {
                case "name":
                    g.setName(str(e));
                    break;
                case "annotations":
                    readStringList(e, fields);
                    g.addAnnotations(fields);
                    break;
                case "addRemainingAnnotations":
                    g.setAddRemainingAnnotations(bool(e));
                    break;
                default:
                    throw new InvalidInputFormatConfig(
                            "Unknown key " + e.getKey() + " in metadata field group " + g.getName() + inFormat());
                }
            }
            cfg.addGroup(g);
        }
    }

    private void readAnnotatedFields(Entry<String, JsonNode> afsEntry, ConfigInputFormat cfg) {
        Iterator<Entry<String, JsonNode>> itFields = obj(afsEntry).fields();
        while (itFields.hasNext()) {
            Entry<String, JsonNode> field = itFields.next();
            String fieldName = field.getKey();
            Iterator<Entry<String, JsonNode>> itField = obj(field).fields();
            ConfigAnnotatedField af = cfg.getOrCreateAnnotatedField(fieldName);
            while (itField.hasNext()) {
                Entry<String, JsonNode> e = itField.next();
                switch (e.getKey()) {
                case "displayName":
                    af.setDisplayName(str(e));
                    break;
                case "description":
                    af.setDescription(str(e));
                    break;
                case "containerPath":
                    af.setContainerPath(str(e));
                    break;
                case "wordPath":
                    af.setWordPath(str(e));
                    break;
                case "tokenPositionIdPath": // old name, REMOVED
                    throw new InvalidInputFormatConfig("Encountered removed key 'tokenPositionIdPath' (rename to 'tokenIdPath') in annotated field " + fieldName + inFormat());
                case "tokenIdPath":
                    af.setTokenIdPath(str(e));
                    break;
                case "punctPath":
                    af.setPunctPath(str(e));
                    break;
                case "annotations":
                    readAnnotations(e, af);
                    break;
                case "standoffAnnotations":
                    readStandoffAnnotations(e, af);
                    break;
                case "spans":
                case "inlineTags":
                    readInlineTags(e, af);
                    break;
                default:
                    throw new InvalidInputFormatConfig(
                            "Unknown key " + e.getKey() + " in annotated field " + fieldName + inFormat());
                }
            }
        }
    }

    private void readAnnotations(Entry<String, JsonNode> annotsEntry, ConfigWithAnnotations af) {
        Iterator<JsonNode> itAnnotations = array(annotsEntry).elements();
        while (itAnnotations.hasNext()) {
            af.addAnnotation(readAnnotation(null, itAnnotations.next()));
        }
    }

    private void readSubAnnotations(Entry<String, JsonNode> saEntry, ConfigAnnotation annot) {
        Iterator<JsonNode> itAnnotations = array(saEntry).elements();
        while (itAnnotations.hasNext()) {
            annot.addSubAnnotation(readAnnotation(annot, itAnnotations.next()));
        }
    }

    private ConfigAnnotation readAnnotation(ConfigAnnotation parentAnnot, JsonNode a) {
        Iterator<Entry<String, JsonNode>> itAnnotation = obj(a, "annotation").fields();
        ConfigAnnotation annot = new ConfigAnnotation();
        while (itAnnotation.hasNext()) {
            Entry<String, JsonNode> e = itAnnotation.next();
            boolean isSubannotation = parentAnnot != null;
            switch (e.getKey()) {
            case "name":
                String name = str(e);
                String fullName = parentAnnot == null ? name : parentAnnot.getName() + AnnotatedFieldNameUtil.SUBANNOTATION_FIELD_PREFIX_SEPARATOR + name;
                annot.setName(warnSanitizeXmlElementName(fullName));
                break;
            case "value":
                annot.setValuePath(fixedStringToXpath(str(e)));
                break;
            case "valuePath":
                annot.setValuePath(str(e));
                break;
            case "captureValuePaths":
                ArrayNode paths = (ArrayNode) e.getValue();
                paths.iterator().forEachRemaining((t) -> annot.addCaptureValuePath(t.asText()));
                break;
            case "forEachPath":
                if (!isSubannotation)
                    throw new InvalidInputFormatConfig("Only subannotations may have forEachPath/namePath" + inFormat());
                annot.setForEachPath(str(e));
                break;
            case "namePath":
                if (!isSubannotation)
                    throw new InvalidInputFormatConfig("Only subannotations may have forEachPath/namePath" + inFormat());
                annot.setName(str(e));
                break;
            case "process":
                annot.setProcess(readProcess(e));
                break;
            case "displayName":
                annot.setDisplayName(str(e));
                break;
            case "description":
                annot.setDescription(str(e));
                break;
            case "basePath":
                if (parentAnnot != null)
                    throw new InvalidInputFormatConfig("Subannotations may not have their own basePath" + inFormat());
                annot.setBasePath(str(e));
                break;
            case "sensitivity":
                if (isSubannotation)
                    throw new InvalidInputFormatConfig("Subannotations may not have their own sensitivity settings" + inFormat());
                annot.setSensitivity(AnnotationSensitivities.fromStringValue(str(e)));
                break;
            case "uiType":
                annot.setUiType(str(e));
                break;
            case "subannotations":
                if (isSubannotation)
                    throw new InvalidInputFormatConfig("Subannotations may not have their own subannotations" + inFormat());
                readSubAnnotations(e, annot);
                break;
            case "forwardIndex":
                annot.setForwardIndex(bool(e));
                break;
            case "multipleValues":
                throw new InvalidInputFormatConfig("multipleValues no longer allowed in .blf.yaml (remove this value; multiple values work automatically)");
            case "allowDuplicateValues":
                throw new InvalidInputFormatConfig("allowDuplicateValues no longer allowed in .blf.yaml (remove this value; duplicates are automatically removed)");
            case "captureXml":
                annot.setCaptureXml(bool(e));
                break;
            case "isInternal":
                annot.setInternal(bool(e));
                break;
            default:
                throw new InvalidInputFormatConfig(
                        "Unknown key " + e.getKey() + " in annotation " + StringUtils.defaultString(annot.getName()));
            }
        }
        return annot;
    }

    /**
     * Convert a fixed string value to an XPath expression yielding that value.
     *
     * @param s fixed string the XPath should evaluate to
     * @return XPath expression
     */
    private String fixedStringToXpath(String s) {
        return "\"" + s.replaceAll("\\\\", "\\\\").replaceAll("\"", "\\\"") + "\"";
    }

    private void readStandoffAnnotations(Entry<String, JsonNode> sasEntry, ConfigAnnotatedField af) {
        Iterator<JsonNode> itAnnotations = array(sasEntry).elements();
        while (itAnnotations.hasNext()) {
            JsonNode as = itAnnotations.next();
            ConfigStandoffAnnotations s = new ConfigStandoffAnnotations();
            Iterator<Entry<String, JsonNode>> it = obj(as, "standoffAnnotation").fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                if (key.equals("spanEndPath")) {
                    // Used to be implicit for annotation/span, so maintain backward compatibility.
                    // Type must be explicitly set for relations though.
                    s.setType(AnnotationType.SPAN);
                }
                switch (key) {
                case "type":
                    s.setType(AnnotationType.fromStringValue(str(e)));
                    break;
                case "path":
                    s.setPath(str(e));
                    break;
                case "refTokenPositionIdPath": // old name, REMOVED
                    throw new InvalidInputFormatConfig("Encountered removed key 'refTokenPositionIdPath' (rename to 'tokenRefPath')");
                case "tokenRefPath":
                case "spanStartPath":   // synonym, used for span annotation
                case "sourcePath":      // synonym, used for relation annotation
                    s.setTokenRefPath(str(e));
                    break;
                case "spanEndPath":
                case "targetPath":      // synonym, used for relation annotation
                    s.setSpanEndPath(str(e));
                    break;
                case "spanEndIsInclusive":
                    s.setSpanEndIsInclusive(bool(e));
                    break;
                case "spanNamePath": // REMOVED
                    throw new InvalidInputFormatConfig("Encountered removed key 'spanNamePath' (rename to 'valuePath')");
                case "valuePath":
                    s.setValuePath(str(e));
                    break;
                case "annotations":
                    readAnnotations(e, s);
                    break;
                case "relationClass":
                    s.setRelationClass(str(e));
                    break;
                case "targetField":
                    s.setTargetField(str(e));
                    break;
                case "targetVersionPath":
                    s.setTargetVersionPath(str(e));
                    break;
                default:
                    throw new InvalidInputFormatConfig(
                            "Unknown key " + key + " in standoff annotations block.");
                }
            }
            af.addStandoffAnnotation(s);
        }
    }

    private void readInlineTags(Entry<String, JsonNode> itsEntry, ConfigAnnotatedField af) {
        Iterator<JsonNode> itTags = array(itsEntry).elements();
        while (itTags.hasNext()) {
            JsonNode as = itTags.next();
            ConfigInlineTag t = new ConfigInlineTag();
            Iterator<Entry<String, JsonNode>> itTag = obj(as, "inlineTag").fields();
            while (itTag.hasNext()) {
                Entry<String, JsonNode> e = itTag.next();
                switch (e.getKey()) {
                case "path":
                    t.setPath(str(e));
                    break;
                case "displayAs":
                    t.setDisplayAs(str(e));
                    break;
                case "tokenIdPath":
                    t.setTokenIdPath(str(e));
                    break;
                case "includeAttributes": // (removed, use attributes)
                    throw new InvalidInputFormatConfig("includeAttributes no longer allowed in .blf.yaml (use 'attributes' instead)");
                case "excludeAttributes": // (removed, use attributes)
                    throw new InvalidInputFormatConfig("excludeAttributes no longer allowed in .blf.yaml (use 'attributes' with exclude: true instead)");
                case "extraAttributes": // (removed, renamed to attributes)
                    throw new InvalidInputFormatConfig("extraAttributes no longer allowed in .blf.yaml (use 'attributes' instead)");
                case "attributes":
                    t.setAttributes(readExtraAttributes(e));
                    break;
                default:
                    throw new InvalidInputFormatConfig("Unknown key " + e.getKey() + " in inline tag " + t.getPath());
                }
            }
            af.addInlineTag(t);
        }
    }

    /**
     * Extra attributes to index by XPath for inline tag
     *
     * @return
     */
    private List<ConfigAttribute> readExtraAttributes(Entry<String, JsonNode> e) {
        List<ConfigAttribute> extraAttr = new ArrayList<>();
        Iterator<JsonNode> itExtraAttr = array(e).elements();
        while (itExtraAttr.hasNext()) {
            JsonNode ea = itExtraAttr.next();
            ConfigAttribute a = new ConfigAttribute();
            Iterator<Entry<String, JsonNode>> itExtraAttrEntry = obj(ea, "extra attribute").fields();
            while (itExtraAttrEntry.hasNext()) {
                Entry<String, JsonNode> eea = itExtraAttrEntry.next();
                switch (eea.getKey()) {
                case "name":
                    a.setName(str(eea));
                    break;
                case "value":
                    a.setValuePath(fixedStringToXpath(str(eea)));
                    break;
                case "valuePath":
                    a.setValuePath(str(eea));
                    break;
                case "process":
                    a.setProcess(readProcess(eea));
                    break;
                default:
                    throw new InvalidInputFormatConfig(
                            "Unknown key " + eea.getKey() + " in extra attribute " + a.getName());
                }
            }
            extraAttr.add(a);
        }
        return extraAttr;
    }

    private void readMetadata(Entry<String, JsonNode> mdEntry, ConfigInputFormat cfg) {
        JsonNode node = mdEntry.getValue();
        if (node instanceof ObjectNode) {
            // Single metadata block
            readMetadataBlock(node, cfg);
        } else if (node instanceof ArrayNode) {
            // List of metadata blocks
            Iterator<JsonNode> itAnnotations = node.elements();
            while (itAnnotations.hasNext()) {
                JsonNode as = itAnnotations.next();
                readMetadataBlock(as, cfg);
            }
        } else {
            throw new InvalidInputFormatConfig("Wrong node type for metadata (must be object or array)");
        }
    }

    private void readMetadataBlock(JsonNode as, ConfigInputFormat cfg) {
        ConfigMetadataBlock b = cfg.createMetadataBlock();
        Iterator<Entry<String, JsonNode>> it = obj(as, "metadata block").fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "containerPath":
                b.setContainerPath(str(e));
                break;
            case "defaultAnalyzer":
                b.setDefaultAnalyzer(str(e));
                break;
            case "fields":
                readMetadataFields(e, b);
                break;
            default:
                throw new InvalidInputFormatConfig("Unknown key " + e.getKey() + " in metadata block");
            }
        }
    }

    private String warnSanitizeXmlElementName(String name) {
        String sanitizedDashAllowed = AnnotatedFieldNameUtil.sanitizeXmlElementName(name);
        String sanitized = AnnotatedFieldNameUtil.sanitizeXmlElementName(name);
        if (sanitizedDashAllowed.equals(name) && name.contains("-")) {
            return sanitizedDashAllowed;
        } else if (!sanitized.equals(name)) {
            logger.warn("Name '" + name + "' is not a valid XML element name; sanitized to '" + sanitized + "'"
                    + inFormat());
        }
        return sanitized;
    }

    private void readMetadataFields(Entry<String, JsonNode> mfsEntry, ConfigMetadataBlock b) {
        Iterator<JsonNode> itFields = array(mfsEntry).elements();
        while (itFields.hasNext()) {
            JsonNode fld = itFields.next();
            String name = fld.has("name") ? fld.get("name").asText() : null;
            ConfigMetadataField f;
            boolean existingField = false;
            if (name != null) {
                // If field exists, modify existing field.
                // This is mostly because of forward references to fields; the field instance
                // would be created by the reference, and the field properties will be added when
                // they are parsed later in the file.
                f = b.getOrCreateField(warnSanitizeXmlElementName(name));
                existingField = true;
            } else
                f = new ConfigMetadataField();
            Iterator<Entry<String, JsonNode>> itField = obj(fld, "metadata field").fields();
            while (itField.hasNext()) {
                Entry<String, JsonNode> e = itField.next();
                switch (e.getKey()) {
                case "name":
                    f.setName(warnSanitizeXmlElementName(str(e)));
                    break;
                case "namePath":
                    f.setName(str(e));
                    break;
                case "value":
                    f.setValuePath(fixedStringToXpath(str(e)));
                    break;
                case "valuePath":
                    f.setValuePath(str(e));
                    break;
                case "forEachPath":
                    f.setForEachPath(str(e));
                    break;
                case "process":
                    f.setProcess(readProcess(e));
                    break;
                case "mapValues": // removed (use "map" processing stap with "table" param)
                    throw new InvalidInputFormatConfig("'mapValues' no longer allowed in .blf.yaml (use 'map' processing step with 'table' param instead; see https://blacklab.ivdnt.org/guide/index-your-data/processing-values.html)");
                case "displayName":
                    f.setDisplayName(str(e));
                    break;
                case "description":
                    f.setDescription(str(e));
                    break;
                case "type":
                    f.setType(FieldType.fromStringValue(str(e)));
                    break;
                case "uiType":
                    f.setUiType(str(e));
                    break;
                case "unknownCondition":
                    f.setUnknownCondition(UnknownCondition.fromStringValue(str(e)));
                    break;
                case "unknownValue":
                    f.setUnknownValue(str(e));
                    break;
                case "analyzer":
                    f.setAnalyzer(str(e));
                    break;
                case "displayOrder":
                    List<String> fields = new ArrayList<>();
                    readStringList(e, fields);
                    f.addDisplayOrder(fields);
                    break;
                case "displayValues":
                    Map<String, String> values = new HashMap<>();
                    readStringMap(e, values);
                    f.addDisplayValues(values);
                    break;
                case "sortValues":
                    f.setSortValues(YamlJsonReader.bool(e));
                    break;
                default:
                    throw new InvalidInputFormatConfig(
                            "Unknown key " + e.getKey() + " in metadata field " + descFormat());
                }
            }
            if (!existingField)
                b.addMetadataField(f);
        }
    }

    private void readLinkedDocuments(Entry<String, JsonNode> ldsEntry, ConfigInputFormat cfg) {
        Iterator<Entry<String, JsonNode>> itLinkedDocs = obj(ldsEntry).fields();
        while (itLinkedDocs.hasNext()) {
            Entry<String, JsonNode> linkedDoc = itLinkedDocs.next();
            ConfigLinkedDocument ld = cfg.getOrCreateLinkedDocument(linkedDoc.getKey());
            Iterator<Entry<String, JsonNode>> itLinkedDoc = obj(linkedDoc).fields();
            while (itLinkedDoc.hasNext()) {
                Entry<String, JsonNode> e = itLinkedDoc.next();
                switch (e.getKey()) {
                case "store":
                    ld.setStore(bool(e));
                    break;
                case "linkValues":
                    readLinkValues(e, ld);
                    break;
                case "ifLinkPathMissing":
                    ld.setIfLinkPathMissing(MissingLinkPathAction.fromStringValue(str(e)));
                    break;
                case "inputFile":
                    ld.setInputFile(str(e));
                    break;
                case "pathInsideArchive":
                    ld.setPathInsideArchive(str(e));
                    break;
                case "documentPath":
                    ld.setDocumentPath(str(e));
                    break;
                case "inputFormat":
                    readInputFormat(ld, e);
                    break;
                default:
                    throw new InvalidInputFormatConfig(
                            "Unknown key " + e.getKey() + " in linked document " + ld.getName());
                }
            }
        }
    }

    private void readInputFormat(ConfigLinkedDocument ld, Entry<String, JsonNode> e) {
        // Resolve the inputFormat right now, instead of potentially failing later when the format is actually needed at some point during indexing
        String formatIdentifier = str(e);
        InputFormat inputFormat = DocumentFormats.getFormat(formatIdentifier).orElseThrow(() -> new InvalidInputFormatConfig(
                "Unknown input format " + formatIdentifier + " in linked document " + ld.getName()));

        ld.setInputFormatIdentifier(formatIdentifier);
    }

    private void readLinkValues(Entry<String, JsonNode> lvsEntry, ConfigLinkedDocument ld) {
        Iterator<JsonNode> itLinkValues = array(lvsEntry).elements();
        while (itLinkValues.hasNext()) {
            JsonNode linkValue = itLinkValues.next();
            ConfigLinkValue lv = new ConfigLinkValue();
            Iterator<Entry<String, JsonNode>> itLinkValue = obj(linkValue, "link value").fields();
            while (itLinkValue.hasNext()) {
                Entry<String, JsonNode> e = itLinkValue.next();
                switch (e.getKey()) {
                case "value":
                    lv.setValuePath(fixedStringToXpath(str(e)));
                    break;
                case "valuePath":
                    lv.setValuePath(str(e));
                    break;
                case "valueField":
                    lv.setValueField(str(e));
                    break;
                case "process":
                    lv.setProcess(readProcess(e));
                    break;
                default:
                    throw new InvalidInputFormatConfig(
                            "Unknown key " + e.getKey() + " in linked document " + ld.getName());
                }
            }
            ld.addLinkValue(lv);
        }
    }

    private List<ConfigProcessStep> readProcess(Entry<String, JsonNode> prEntry) {
        Iterator<JsonNode> itSteps = array(prEntry).elements();
        List<ConfigProcessStep> p = new ArrayList<>();
        while (itSteps.hasNext()) {
            JsonNode step = itSteps.next();
            ConfigProcessStep s = new ConfigProcessStep();
            Iterator<Entry<String, JsonNode>> itStep = obj(step, "processing step").fields();
            while (itStep.hasNext()) {
                Entry<String, JsonNode> e = itStep.next();
                switch (e.getKey()) {
                case "action":
                    s.setMethod(str(e));
                    break;
                default:
                    s.addParam(e.getKey(), stringOrStringMap(e));
                    break;
                }
            }
            p.add(s);
        }

        return p;
    }
}
