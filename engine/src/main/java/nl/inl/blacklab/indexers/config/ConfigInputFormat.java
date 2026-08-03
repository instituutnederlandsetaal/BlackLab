package nl.inl.blacklab.indexers.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.InputFormatInfo;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;

/**
 * Configuration for an input format (either contents, or metadata, or a mix of
 * both).
 */
public class ConfigInputFormat {

    private static final Logger logger = LogManager.getLogger(ConfigInputFormat.class);

    public static ConfigInputFormat read(String formatFileContents, boolean isJson, String formatIdentifier, File readFromFile) {
        assert formatIdentifier != null;
        ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
        ConfigInputFormat config;
        try {
            config = mapper.readValue(formatFileContents, ConfigInputFormat.class);
            config.setName(formatIdentifier);
            InputFormatMessages messages = new InputFormatMessages();
            config.finalizeAndValidate(messages);
            if (!messages.getErrors().isEmpty()) {
                String combined = String.join("; ", messages.getErrors());
                throw new InvalidInputFormatConfig(combined);
            } else if (!messages.getWarnings().isEmpty()) {
                messages.log(logger, formatIdentifier);
            }
            if (readFromFile != null)
                config.setReadFromFile(readFromFile);
            else
                config.setFormatFileContents(formatFileContents);
            return config;
        } catch (Exception e) {
            throw InvalidInputFormatConfig.withFormatIdentifier(e, formatIdentifier);
        }
    }

    /**
     * Reads a config from a YAML or JSON reader.
     *
     * @param reader the reader to read from
     * @param isJson true if the reader is JSON, false if YAML
     * @param formatIdentifier the name to give this format
     * @param readFromFile file this was read from (to get full contents later to store in index)
     * @return the config
     */
    public static ConfigInputFormat read(Reader reader, boolean isJson, String formatIdentifier, File readFromFile) {
        try {
            String formatFileContents = IOUtils.toString(reader);
            return read(formatFileContents, isJson, formatIdentifier, readFromFile);
        } catch (Exception e) {
            throw InvalidInputFormatConfig.withFormatIdentifier(e, formatIdentifier);
        }
    }

    /**
     * Reads a config from a YAML or JSON file.
     *
     * @param file the file to read
     * @param formatIdentifier the name to give this format
     * @return the config
     * @throws InvalidInputFormatConfig if the file is not a valid config
     */
    public static ConfigInputFormat read(File file, String formatIdentifier)  {
        try {
            assert file != null;
            BufferedReader reader = FileUtil.openForReading(file);
            boolean isJson = file.getName().endsWith(".json");
            ConfigInputFormat cfg = read(reader, isJson, formatIdentifier, file);
            return cfg;
        } catch (Exception e) {
            throw InvalidInputFormatConfig.withFormatFile(e, file);
        }
    }

    /**
     * Read a config from a YAML or JSON file.
     *
     * The name of this file (minus the .blf.* extension) will be used as this format's name.
     *
     * @param file the file to read
     * @return the config
     * @throws InvalidInputFormatConfig if the file is not a valid config
     */
    public static ConfigInputFormat read(File file) {
        return read(file, FormatFileNameUtil.stripExtensions(file.getName()));
    }

    /** Basic file types we support */
    public enum FileType {
        XML,
        TABULAR, // csv, tsv
        TEXT, // plain text
        CHAT, // CHILDES CHAT format
        CONLL_U; // CoNLL-U format

        @JsonCreator
        public static FileType fromStringValue(String str) {
            return valueOf(str.toUpperCase().replace("-", "_"));
        }

        @JsonValue
        public String stringValue() {
            return toString().toLowerCase().replace("_", "-");
        }
    }

    public static final int MIN_VERSION = 2;

    public static final int MAX_VERSION = 2;

    @JsonPropertyDescription("Format version number (optional, defaults to latest).")
    private int version = MAX_VERSION;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        if (version < MIN_VERSION || version > MAX_VERSION)
            throw new InvalidInputFormatConfig("Unsupported version number: " + version);
        this.version = version;
    }

    /**
     * This format's name.
     */
    @JsonIgnore
    private String name;

    public void setName(String formatIdentifier) {
        this.name = formatIdentifier;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyDescription("Display name for this format (optional)")
    private String displayName = "";

    /** This format's description (optional) */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyDescription("Description of this format (optional)")
    private String description = "";

    /**
     * Link to a help page, e.g. showing an example of a correct input file
     * (optional)
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyDescription("URL where more information about the format can be found (optional)")
    private String helpUrl = "";

    /**
     * Should this format be visible in the list of formats.
     * <p>
     * Used to set {@link InputFormatInfo#getIsVisible()}, to indicate internal formats to client
     * applications, but has no other internal meaning.
     */
    @JsonPropertyDescription("Whether or not to show the format in the format list (default: true)")
    private boolean isVisible = true;

    /**
     * This format's type indicator (optional, not used by BlackLab. usually
     * 'contents' or 'metadata')
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyDescription("Type of format, contents or metadata (deprecated, not used by BlackLab)")
    private String type = "";

    /** Ids of FileConverter plugins to be applied in order before indexing the file. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyDescription("Converter plugins to apply before indexing (optional)")
    private List<Map<String, Object>> converters = new ArrayList<>();

    /** id of a {@link FileConverter} to run files through prior to indexing */
    @JsonPropertyDescription("Deprecated; use converters list instead")
    private String convertPluginId;

    /**
     * id of a {@link FileConverter} to run files through prior to indexing, this
     * happens after converting (if applicable)
     */
    @JsonPropertyDescription("Deprecated; use converters list instead")
    private String tagPluginId;

    /**
     * What type of file is this (e.g. xml, tabular, plaintext)? Determines subclass
     * of {@link InputFormatTypeConfig} to instantiate
     */
    @JsonPropertyDescription("Input file type, e.g. xml/tabular/text (default: xml)")
    private FileType fileType = FileType.XML;

    /** Options for the file type (i.e. separator in case of tabular, etc.) */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyDescription("Any options specific to the file type, such as the separator for tabular file formats (optional)")
    private final Map<String, String> fileTypeOptions = new HashMap<>();

    /** XML processor to use (deprecated, we always use Saxon) */
    @JsonPropertyDescription("XML processor to use (deprecated, always Saxon)")
    String processor = null;

    public void setProcessor(String processor) {
        this.processor = processor;
    }

    /** XML namespace declarations */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyDescription("Any XML namespaces used in XPaths. Omit to ignore namespaces.")
    final Map<String, String> namespaces = new LinkedHashMap<>();

    /** How to find our documents */
    @JsonPropertyDescription("XPath to document(s) in an input file (default: /)")
    private String documentPath = "/";

    /** Should we store the document in the content store? (default: yes) */
    @JsonPropertyDescription("Should we store the document in the content store? (default: true)")
    private boolean store = true;

    /**
     * Before adding metadata fields to the document, this name mapping is applied.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyDescription("Metadata field name mapping. Deprecated, use processing step on name path instead.")
    final Map<String, String> indexFieldAs = new LinkedHashMap<>();

    /** What default analyzer to use if not overridden */
    @JsonInclude(value=JsonInclude.Include.CUSTOM, valueFilter= JsonFilters.IsDefault.class)
    @JsonPropertyDescription("Default analyzer to use for metadata fields (default: DEFAULT)")
    private String metadataDefaultAnalyzer = "DEFAULT";

    @JsonPropertyDescription("When to use the default unknown value for metadata fields")
    private UnknownCondition metadataDefaultUnknownCondition = UnknownCondition.NEVER;

    @JsonInclude(value=JsonInclude.Include.CUSTOM, valueFilter= JsonFilters.IsUnknown.class)
    @JsonPropertyDescription("The unknown value to use for metadata fields")
    private String metadataDefaultUnknownValue = "unknown";

    public static class MetadataDeserializer extends StdDeserializer<List<ConfigMetadataBlock>> {
        public MetadataDeserializer() {
            super(List.class);
        }

        @Override
        public List<ConfigMetadataBlock> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            ObjectCodec codec = p.getCodec();
            JsonNode node = codec.readTree(p);
            List<ConfigMetadataBlock> result = new ArrayList<>();
            if (node.isObject()) {
                // Single object: wrap it in a list
                result.add(codec.treeToValue(node, ConfigMetadataBlock.class));
            } else if (node.isArray()) {
                // List of objects: parse as usual
                for (JsonNode element : node) {
                    result.add(codec.treeToValue(element, ConfigMetadataBlock.class));
                }
            }
            return result;
        }
    }

    /** Annotated fields (usually just "contents") */
    @JsonPropertyDescription("Annotated fields in the document, usually just one, often named 'contents'")
    private final Map<String, ConfigAnnotatedField> annotatedFields = new LinkedHashMap<>();

    public void setAnnotatedFields(Map<String, ConfigAnnotatedField> annotatedFields) {
        this.annotatedFields.clear();
        this.annotatedFields.putAll(annotatedFields);
        for (Map.Entry<String, ConfigAnnotatedField> entry : annotatedFields.entrySet()) {
            // Make sure each field knows its name
            entry.getValue().setName(entry.getKey());
        }
    }

    /** Blocks of embedded metadata */
    @JsonDeserialize(using = MetadataDeserializer.class)
    @JsonPropertyDescription("Block(s) that configure how to index metadata fields.")
    private final List<ConfigMetadataBlock> metadata = new ArrayList<>();

    /** Linked document(s), e.g. containing our metadata */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyDescription("Linked documents. Deprecated, use XPath 3 doc() function instead.")
    private final Map<String, ConfigLinkedDocument> linkedDocuments = new LinkedHashMap<>();

    /** Configuration that will be added to indexmetadata when creating a corpus */
    @JsonPropertyDescription("Corpus-level metadata, such as the persistent identifier (pidField) and text direction.")
    private final ConfigCorpus corpusConfig = new ConfigCorpus();

    /**
     * What file was this format read from? Useful if we want to display it in BLS.
     */
    @JsonIgnore
    private File readFromFile;

    /** Full contents of the format file, if readFromFile == null */
    @JsonIgnore
    private String formatFileContents;

    /**
     * Construct empty input format instance.
     */
    public ConfigInputFormat() {
        this("UNKNOWN");
    }

    /**
     * Construct empty input format instance.
     * 
     * @param name format name
     */
    public ConfigInputFormat(String name) {
        this.name = name;
    }

    @JsonIgnore
    public String getOriginalFileContents() {
        try {
            if (formatFileContents != null)
                return formatFileContents;
            if (readFromFile == null)
                return "(configuration file not available)";
            return IOUtils.toString(getFormatFile());
        } catch (IOException e) {
            throw new InvalidInputFormatConfig(e);
        }
    }

    private void finalizeAndValidate(InputFormatMessages messages) {
        // Ensure that if we have any linked documents we want to store (like metadata), there exists an
        // annotated field where we can store it (even if it has no annotations).
        for (Map.Entry<String, ConfigLinkedDocument> e: linkedDocuments.entrySet()) {
            ConfigLinkedDocument ld = e.getValue();
            ld.setName(e.getKey());
            if (ld.shouldStore() && getAnnotatedField(ld.getName()) == null) {
                // Field doesn't exit yet. Create a dummy field for it.
                addAnnotatedField(ConfigAnnotatedField.createDummyForStoringLinkedDocument(ld.getName()));
            }
        }

        // Default to Saxon if no processor specified
        if (getFileType() == FileType.XML &&
                getFileTypeOptions().get(InputFormatTypeXml.FT_OPT_PROCESSOR) == null) {
            addFileTypeOption(InputFormatTypeXml.FT_OPT_PROCESSOR, InputFormatTypeXml.PROCESSOR_NAME);
        }

        // Validate
        String t = "input format";
        messages.mustHave(t, name, "name");
        messages.mustHave(t, documentPath, "documentPath");
        for (ConfigMetadataBlock b : metadata)
            b.validate(messages);
        for (ConfigAnnotatedField af : annotatedFields.values()) {
            if (fileType != FileType.XML)
                af.setWordPath("N/A"); // prevent validation error
            af.validate(messages);
        }
        for (Map.Entry<String, ConfigLinkedDocument> e : linkedDocuments.entrySet()) {
            ConfigLinkedDocument ld = e.getValue();
            ld.setName(e.getKey());
            ld.validate(messages);
        }

        if (processor != null)
            messages.warning("encountered 'processor' key (this is ignored by BlackLab v5+, it only supports the Saxon processor)");
        if (!linkedDocuments.isEmpty())
            messages.warning("'linkedDocuments' section is deprecated; use XPath 3 doc() function instead (see https://blacklab.ivdnt.org/guide/index-your-data/metadata)");
        if (!indexFieldAs.isEmpty())
            messages.warning("'indexFieldAs' mapping is deprecated; use forEach with nameProcess (action 'map') instead (see https://blacklab.ivdnt.org/guide/index-your-data/processing-values.html)");
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    @JsonInclude(value=JsonInclude.Include.CUSTOM, valueFilter= JsonFilters.IsTrue.class)
    public boolean getIsVisible() {
        return isVisible;
    }

    public void setIsVisible(boolean visible) {
        this.isVisible = visible;
    }

    public void setDocumentPath(String documentPath) {
        this.documentPath = documentPath;
    }

    void addMetadataBlock(ConfigMetadataBlock b) {
        if (b.getAnalyzer().isEmpty())
            b.setDefaultAnalyzer(metadataDefaultAnalyzer);
        metadata.add(b);
    }

    public ConfigMetadataBlock createMetadataBlock() {
        ConfigMetadataBlock b = new ConfigMetadataBlock();
        b.setDefaultAnalyzer(metadataDefaultAnalyzer);
        metadata.add(b);
        return b;
    }

    public Map<String, ConfigAnnotatedField> getAnnotatedFields() {
        return Collections.unmodifiableMap(annotatedFields);
    }

    public List<ConfigMetadataBlock> getMetadata() {
        return Collections.unmodifiableList(metadata);
    }

    public void addAnnotatedField(ConfigAnnotatedField f) {
        this.annotatedFields.put(f.getName(), f);
    }

    public void setConverters(List<Map<String, Object>> converters) {
        this.converters.clear();
        this.converters.addAll(converters);
    }

    public void setConvertPluginId(String id) {
        logger.warn("'convertPlugin' key in input format config is deprecated; please use 'converters' list instead.");
        this.convertPluginId = id;
    }

    public void setTagPluginId(String id) {
        logger.warn("'tagPlugin' key in input format config is deprecated; please use 'converters' list instead.");
        this.tagPluginId = id;
    }

    String KEY_CONVERTER_ID = "id";

    public List<Map<String, Object>> getConverters() {
        List<Map<String, Object>> converters = new ArrayList<>(this.converters);

        // Older style: single convert and/or tag plugin
        if (convertPluginId != null && !convertPluginId.isEmpty())
            converters.add(Map.of(KEY_CONVERTER_ID, convertPluginId));
        if (tagPluginId != null && !tagPluginId.isEmpty())
            converters.add(Map.of(KEY_CONVERTER_ID, tagPluginId));

        return converters;
    }

    public boolean hasFileConverters() {
        return !converters.isEmpty() || !StringUtils.isEmpty(convertPluginId) || !StringUtils.isEmpty(tagPluginId);
    }

    @JsonIgnore
    public boolean isNamespaceAware() {
        return !namespaces.isEmpty();
    }

    public Map<String, String> getNamespaces() {
        return namespaces;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public ConfigAnnotatedField getAnnotatedField(String name) {
        return getAnnotatedField(name, false);
    }

    private ConfigAnnotatedField getAnnotatedField(String name, boolean createIfNotFound) {
        ConfigAnnotatedField f = annotatedFields.get(name);
        if (f == null && createIfNotFound) {
            f = new ConfigAnnotatedField(name);
            annotatedFields.put(name, f);
        }
        return f;
    }

    public ConfigAnnotatedField getOrCreateAnnotatedField(String name) {
        return getAnnotatedField(name, true);
    }

    public Map<String, ConfigLinkedDocument> getLinkedDocuments() {
        return Collections.unmodifiableMap(linkedDocuments);
    }

    public void setLinkedDocuments(Map<String, ConfigLinkedDocument> linkedDocuments) {
        this.linkedDocuments.clear();
        for (Map.Entry<String, ConfigLinkedDocument> e : linkedDocuments.entrySet()) {
            e.getValue().setName(e.getKey());
        }
        this.linkedDocuments.putAll(linkedDocuments);
    }

    private ConfigLinkedDocument getLinkedDocument(String name, boolean createIfNotFound) {
        ConfigLinkedDocument ld = linkedDocuments.get(name);
        if (ld == null && createIfNotFound) {
            ld = new ConfigLinkedDocument(name);
            linkedDocuments.put(name, ld);
        }
        return ld;
    }

    public ConfigLinkedDocument getOrCreateLinkedDocument(String name) {
        return getLinkedDocument(name, true);
    }

    public Map<String, String> getIndexFieldAs() {
        return Collections.unmodifiableMap(indexFieldAs);
    }

    public boolean shouldStore() {
        return store;
    }

    public void setStore(boolean store) {
        this.store = store;
    }

    public String getMetadataDefaultAnalyzer() {
        return metadataDefaultAnalyzer;
    }

    public void setMetadataDefaultAnalyzer(String metadataDefaultAnalyzer) {
        this.metadataDefaultAnalyzer = metadataDefaultAnalyzer;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, String> getFileTypeOptions() {
        return fileTypeOptions;
    }

    public void addFileTypeOption(String key, String value) {
        this.fileTypeOptions.put(key, value);
    }

    public ConfigCorpus getCorpusConfig() {
        return corpusConfig;
    }

    public ConfigMetadataField getMetadataField(String fieldname) {
        for (ConfigMetadataBlock bl : metadata) {
            ConfigMetadataField f = bl.getMetadataField(fieldname);
            if (f != null)
                return f;
        }
        return null;
    }

    public File getReadFromFile() {
        return readFromFile;
    }

    @JsonIgnore
    public BufferedReader getFormatFile() {
        try {
            if (readFromFile == null)
                return null;

            if (readFromFile.getPath().startsWith("$BLACKLAB_JAR")) {
                InputStream stream = DocumentFormats.class.getClassLoader()
                        .getResourceAsStream("formats/" + FormatFileNameUtil.yamlFormatFileName(getName()));
                return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
            return FileUtil.openForReading(readFromFile);
        } catch (FileNotFoundException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    public void setReadFromFile(File readFromFile) {
        this.readFromFile = readFromFile;
    }

    public void setFormatFileContents(String formatFileContents) {
        this.formatFileContents = formatFileContents;
    }

    public String getHelpUrl() {
        return helpUrl;
    }

    public void setHelpUrl(String helpUrl) {
        this.helpUrl = helpUrl;
    }

    @Override
    public String toString() {
        return "ConfigInputFormat [name=" + name + "]";
    }

    public UnknownCondition getMetadataDefaultUnknownCondition() {
        return metadataDefaultUnknownCondition;
    }

    public String getMetadataDefaultUnknownValue() {
        return metadataDefaultUnknownValue;
    }

    public void setMetadataDefaultUnknownCondition(UnknownCondition unknownCondition) {
        this.metadataDefaultUnknownCondition = unknownCondition;
    }

    public void setMetadataDefaultUnknownValue(String unknownValue) {
        this.metadataDefaultUnknownValue = unknownValue;
    }

    @JsonIgnore
    public String getConfigFileType() {
        return FilenameUtils.getExtension(getReadFromFile().getName()).toLowerCase();
    }

    public void setBaseFormat(String baseFormatName) {
        throw new InvalidInputFormatConfig("Input format configuration inheritance ('baseFormat' key) was removed. " +
                "Please copy the base format configuration to your own format and customize it.");
    }

}
