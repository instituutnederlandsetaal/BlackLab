package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.indexers.config.process.ProcessingStep;
import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;
import nl.inl.util.XPathUtil;

/** Configuration for metadata field(s). */
public class ConfigMetadataField {

    /** Metadata field name (if not forEach) */
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getName() {
        return name;
    }

    /** XPath to determine metadata field name (if forEach) */
    private String namePath;

    public void setNamePath(String namePath) {
        this.namePath = namePath;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getNamePath() {
        return namePath;
    }

    /** How to display the field in the interface (optional) */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String displayName = "";

    /** How to describe the field in the interface (optional) */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String description = "";

    /**
     * If null: regular metadata field definition. Otherwise, find all nodes
     * matching this XPath, then evaluate fieldName and valuePath as XPaths for each
     * matching node.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String forEachPath;

    /** Where to find metadata value */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String valuePath;

    /** How to process annotation values (if at all) */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<ConfigProcessStep> process = new ArrayList<>();

    @JsonIgnore
    ProcessingStep processSteps = ProcessingInstruction.identity();

    /** How to process namePath value (if at all) */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<ConfigProcessStep> nameProcess = new ArrayList<>();

    @JsonIgnore
    ProcessingStep nameProcessSteps = ProcessingInstruction.identity();

    /** How to index the field (tokenized|untokenized|numeric) */
    private FieldType type = FieldType.TOKENIZED;

    /**
     * When to index the unknownValue: NEVER|MISSING|EMPTY|MISSING_OR_EMPTY
     * (null = use configured default value)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UnknownCondition unknownCondition = null;

    /** What to index when unknownCondition is true (null = use configured default value) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String unknownValue = null;

    /** Analyzer to use for this field */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String analyzer = "";

    /** What UI element to show in the interface (optional) */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String uiType = "";

    /** Mapping from value to displayValue (optional) */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Map<String, String> displayValues = new HashMap<>();

    /** Order in which to display the values (optional) */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<String> displayOrder = new ArrayList<>();

    /**
     * Whether to sort multiple value alphabetically or preserve them in document order
     * This reflects on order of values for this field returned by lucene (and by extension, BlackLab and BlackLab server)
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean sortValues = false;

    public ConfigMetadataField() {
    }

    public ConfigMetadataField(String name, String valuePath) {
        this(name, valuePath, null);
    }

    public ConfigMetadataField(String fieldName, String valuePath, String forEachPath) {
        setName(fieldName);
        setValuePath(valuePath);
        setForEachPath(forEachPath);
    }

    public ConfigMetadataField copy() {
        ConfigMetadataField cp = new ConfigMetadataField(name, valuePath, forEachPath);
        cp.setProcess(process);
        cp.setNameProcess(nameProcess);
        cp.setDisplayName(displayName);
        cp.setDescription(description);
        cp.setType(type);
        cp.setUiType(uiType);
        cp.setUnknownCondition(unknownCondition);
        cp.setUnknownValue(unknownValue);
        cp.setAnalyzer(analyzer);
        cp.displayValues.putAll(displayValues);
        cp.displayOrder.addAll(displayOrder);
        cp.setSortValues(sortValues);
        return cp;
    }

    void validate(InputFormatMessages messages) throws InvalidInputFormatConfig {
        String t = "metadata field";
        if (isForEach())
            messages.mustHave(t, namePath, "namePath");
        else
            messages.mustHave(t, name, "name");
        for (ConfigProcessStep step : process) {
            step.validate(messages);
        }
        for (ConfigProcessStep step : nameProcess) {
            step.validate(messages);
        }
    }

    public void setForEachPath(String forEachPath) {
        this.forEachPath = forEachPath;
    }

    public void setValue(String value) {
        this.valuePath = XPathUtil.fixedStringToXpath(value);
    }

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

    public String getValuePath() {
        return valuePath;
    }

    public String getForEachPath() {
        return forEachPath;
    }

    @JsonIgnore
    public boolean isForEach() {
        return forEachPath != null;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUiType() {
        return uiType;
    }

    public void setUiType(String uiType) {
        this.uiType = uiType;
    }

    public UnknownCondition getUnknownCondition() {
        return unknownCondition;
    }

    public void setUnknownCondition(UnknownCondition unknownCondition) {
        this.unknownCondition = unknownCondition;
    }

    public String getUnknownValue() {
        return unknownValue;
    }

    public void setUnknownValue(String unknownValue) {
        this.unknownValue = unknownValue;
    }

    public String getAnalyzer() {
        return analyzer;
    }

    public void setAnalyzer(String analyzer) {
        this.analyzer = analyzer;
    }

    // Only include in JSON serialization if not FieldType.TOKENIZED
    @JsonInclude(value=JsonInclude.Include.CUSTOM, valueFilter= JsonFilters.IsTokenized.class)
    public FieldType getType() {
        return type;
    }

    public void setType(FieldType type) {
        this.type = type;
    }

    public Map<String, String> getDisplayValues() {
        return Collections.unmodifiableMap(displayValues);
    }

    public void addDisplayValues(Map<String, String> displayValues) {
        this.displayValues.putAll(displayValues);
    }

    public List<String> getDisplayOrder() {
        return Collections.unmodifiableList(displayOrder);
    }

    public boolean getSortValues() {
        return sortValues;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ConfigProcessStep> getProcess() {
        return Collections.unmodifiableList(process);
    }

    @JsonIgnore
    public ProcessingStep getCompiledProcessSteps() {
        // We don't synchronize reads, as processSteps is only set once when config is read
        return processSteps;
    }

    public void setProcess(List<ConfigProcessStep> process) {
        this.process.clear();
        this.process.addAll(process);
        processSteps = ProcessingInstruction.fromConfig(process);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ConfigProcessStep> getNameProcess() {
        return Collections.unmodifiableList(nameProcess);
    }

    @JsonIgnore
    public ProcessingStep getCompiledNameProcessSteps() {
        // We don't synchronize reads, as processSteps is only set once when config is read
        return nameProcessSteps;
    }

    public void setNameProcess(List<ConfigProcessStep> nameProcess) {
        this.nameProcess.clear();
        this.nameProcess.addAll(nameProcess);
        nameProcessSteps = ProcessingInstruction.fromConfig(nameProcess);
    }

    public void addDisplayOrder(List<String> fields) {
        displayOrder.addAll(fields);
    }

    public void setSortValues(boolean sortValues) {
        this.sortValues = sortValues;
    }

    @Override
    public String toString() {
        return "ConfigMetadataField [name=" + name + "]";
    }

    public void setMapValues(Map<String, String> mapValues) {
        throw new InvalidInputFormatConfig("'mapValues' no longer allowed in .blf.yaml (use 'map' processing step with 'table' param instead; see https://blacklab.ivdnt.org/guide/index-your-data/processing-values.html) ");
    }

}
