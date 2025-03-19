package nl.inl.blacklab.indexers.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.index.DocIndexerLegacy;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.indexers.preprocess.ConvertPlugin;
import nl.inl.blacklab.indexers.preprocess.TagPlugin;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;
import nl.inl.util.FileUtil;

/**
 * Configuration for an input format (either contents, or metadata, or a mix of
 * both).
 */
public class ConfigInputFormat {

    /** Basic file types we support */
    public enum FileType {
        XML,
        TABULAR, // csv, tsv
        TEXT, // plain text
        CHAT, // CHILDES CHAT format
        CONLL_U; // CoNLL-U format

        public static FileType fromStringValue(String str) {
            return valueOf(str.toUpperCase().replaceAll("-", "_"));
        }

        public String stringValue() {
            return toString().toLowerCase().replaceAll("_", "-");
        }
    }

    /** .blf.yaml config file version */
    private int version = MIN_VERSION;

    /**
     * This format's name, final to ensure consistency within DocIndexerFactories
     */
    private final String name;

    /** This format's display name (optional) */
    private String displayName = "";

    /** This format's description (optional) */
    private String description = "";

    /**
     * Link to a help page, e.g. showing an example of a correct input file
     * (optional)
     */
    private String helpUrl = "";

    /**
     * Should this format be marked as hidden? Mirrors
     * {@link DocIndexerLegacy#isVisible(Class)}. Used to set
     * {@link InputFormat#isVisible()}, to indicate internal formats to client
     * applications, but has no other internal meaning.
     */
    private boolean visible = true;

    /**
     * This format's type indicator (optional, not used by BlackLab. usually
     * 'contents' or 'metadata')
     */
    private String type = "";

    /**
     * What type of file is this (e.g. xml, tabular, plaintext)? Determines subclass
     * of DocIndexerConfig to instantiate
     */
    private FileType fileType = FileType.XML;

    /** Options for the file type (i.e. separator in case of tabular, etc.) */
    private final Map<String, String> fileTypeOptions = new HashMap<>();

    /** Configuration that will be added to indexmetadata when creating a corpus */
    private ConfigCorpus corpusConfig = new ConfigCorpus();

    /** XML namespace declarations */
    final Map<String, String> namespaces = new LinkedHashMap<>();

    /** How to find our documents */
    private String documentPath = "/";

    /** Should we store the document in the content store? (default: yes) */
    private boolean store = true;

    /**
     * Before adding metadata fields to the document, this name mapping is applied.
     */
    final Map<String, String> indexFieldAs = new LinkedHashMap<>();

    /** What default analyzer to use if not overridden */
    private String metadataDefaultAnalyzer = "DEFAULT";

    private UnknownCondition metadataDefaultUnknownCondition = UnknownCondition.NEVER;

    private String metadataDefaultUnknownValue = "unknown";

    /** Blocks of embedded metadata */
    private final List<ConfigMetadataBlock> metadataBlocks = new ArrayList<>();

    /** Annotated fields (usually just "contents") */
    private final Map<String, ConfigAnnotatedField> annotatedFields = new LinkedHashMap<>();

    /** Linked document(s), e.g. containing our metadata */
    private final Map<String, ConfigLinkedDocument> linkedDocuments = new LinkedHashMap<>();

    /** id of a {@link ConvertPlugin} to run files through prior to indexing */
    private String convertPluginId;

    /**
     * id of a {@link TagPlugin} to run files through prior to indexing, this
     * happens after converting (if applicable)
     */
    private String tagPluginId;

    /**
     * What file was this format read from? Useful if we want to display it in BLS.
     */
    private File readFromFile;

    /**
     * Construct empty input format instance.
     * 
     * @param name format name
     */
    public ConfigInputFormat(String name) {
        this.name = name;
    }

    public static final int MIN_VERSION = 1;

    public static final int MAX_VERSION = 2;

    public void setVersion(int version) {
        if (version < MIN_VERSION || version > MAX_VERSION)
            throw new InvalidInputFormatConfig("Invalid version number: " + version);
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    public String getOriginalFileContents() {
        try {
            if (readFromFile == null)
                return "(configuration file not available)";
            return IOUtils.toString(getFormatFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * @param file the file to read, the name of this file (minus the .blf.*
     *            extension) will be used as this format's name.
     * @throws IOException on error
     */
    public ConfigInputFormat(File file) throws IOException {
        this.readFromFile = file;
        this.name = ConfigInputFormat.stripExtensions(file.getName());
        InputFormatReader.read(file, this);
    }

    /**
     * Copy everything except name, displayName and description from the specified
     * format.
     * 
     * @param baseFormat format to copy from
     */
    public void setBaseFormat(ConfigInputFormat baseFormat) {
        type = baseFormat.getType();
        fileType = baseFormat.getFileType();
        if (baseFormat.getFileTypeOptions() != null)
            fileTypeOptions.putAll(baseFormat.getFileTypeOptions());
        corpusConfig = baseFormat.corpusConfig.copy();
        namespaces.putAll(baseFormat.getNamespaces());
        documentPath = baseFormat.getDocumentPath();
        store = baseFormat.shouldStore();
        indexFieldAs.putAll(baseFormat.getIndexFieldAs());
        metadataDefaultAnalyzer = baseFormat.getMetadataDefaultAnalyzer();
        metadataDefaultUnknownCondition = baseFormat.getMetadataDefaultUnknownCondition();
        metadataDefaultUnknownValue = baseFormat.getMetadataDefaultUnknownValue();
        for (ConfigMetadataBlock b : baseFormat.getMetadataBlocks()) {
            addMetadataBlock(b.copy());
        }
        for (ConfigAnnotatedField f : baseFormat.getAnnotatedFields().values()) {
            addAnnotatedField(f.copy());
        }
        linkedDocuments.putAll(baseFormat.getLinkedDocuments());
        setVisible(baseFormat.isVisible());
    }

    /**
     * Validate this configuration.
     */
    public void validate() {
        String t = "input format";
        req(name, t, "name");
        req(documentPath, t, "documentPath");
        for (ConfigMetadataBlock b : metadataBlocks)
            b.validate();
        for (ConfigAnnotatedField af : annotatedFields.values()) {
            if (fileType != FileType.XML)
                af.setWordPath("N/A"); // prevent validation error
            af.validate();
        }
        for (ConfigLinkedDocument ld : linkedDocuments.values())
            ld.validate();
    }

    static void req(String value, String type, String name) {
        if (value == null || value.isEmpty())
            throw new InvalidInputFormatConfig(StringUtils.capitalize(type) + " must have a " + name);
    }

    static void req(boolean test, String type, String mustMsg) {
        if (!test)
            throw new InvalidInputFormatConfig(StringUtils.capitalize(type) + " must " + mustMsg);
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

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean listed) {
        this.visible = listed;
    }

    public void setDocumentPath(String documentPath) {
        this.documentPath = documentPath;
    }

    void addMetadataBlock(ConfigMetadataBlock b) {
        if (b.getAnalyzer().isEmpty())
            b.setDefaultAnalyzer(metadataDefaultAnalyzer);
        metadataBlocks.add(b);
    }

    public ConfigMetadataBlock createMetadataBlock() {
        ConfigMetadataBlock b = new ConfigMetadataBlock();
        b.setDefaultAnalyzer(metadataDefaultAnalyzer);
        metadataBlocks.add(b);
        return b;
    }

    public List<ConfigMetadataBlock> getMetadataBlocks() {
        return Collections.unmodifiableList(metadataBlocks);
    }

    public void addAnnotatedField(ConfigAnnotatedField f) {
        this.annotatedFields.put(f.getName(), f);
    }

    public void setConvertPluginId(String id) {
        this.convertPluginId = id;
    }

    public String getConvertPluginId() {
        return convertPluginId;
    }

    public void setTagPluginId(String id) {
        this.tagPluginId = id;
    }

    public String getTagPluginId() {
        return tagPluginId;
    }

    public boolean isNamespaceAware() {
        return !namespaces.isEmpty();
    }

    public Map<String, String> getNamespaces() {
        return namespaces;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public Map<String, ConfigAnnotatedField> getAnnotatedFields() {
        return Collections.unmodifiableMap(annotatedFields);
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
        for (ConfigMetadataBlock bl : metadataBlocks) {
            ConfigMetadataField f = bl.getMetadataField(fieldname);
            if (f != null)
                return f;
        }
        return null;
    }

    public static String stripExtensions(String fileName) {
        String name = fileName.replaceAll("\\.(ya?ml|json)$", "");
        if (name.endsWith(".blf"))
            return name.substring(0, name.length() - 4);
        return name;
    }

    public File getReadFromFile() {
        return readFromFile;
    }

    public BufferedReader getFormatFile() {
        try {
            if (readFromFile == null)
                return null;

            if (readFromFile.getPath().startsWith("$BLACKLAB_JAR")) {
                InputStream stream = DocumentFormats.class.getClassLoader()
                        .getResourceAsStream("formats/" + getName() + ".blf.yaml");
                return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
            return FileUtil.openForReading(readFromFile);
        } catch (FileNotFoundException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    public void setReadFromFile(File readFromFile) {
        this.readFromFile = readFromFile;
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

    public String getConfigFileType() {
        return FilenameUtils.getExtension(getReadFromFile().getName()).toLowerCase();
    }

}
