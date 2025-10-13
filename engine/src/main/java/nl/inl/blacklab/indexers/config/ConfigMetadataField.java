package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.indexers.config.process.ProcessingStep;
import nl.inl.blacklab.indexers.config.process.ProcessingStepMapValues;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;

/** Configuration for metadata field(s). */
public class ConfigMetadataField {

    /** Metadata field name (or name XPath, if forEach) */
    private String name;

    /** Where to find metadata value */
    private String valuePath;

    /**
     * If null: regular metadata field definition. Otherwise, find all nodes
     * matching this XPath, then evaluate fieldName and valuePath as XPaths for each
     * matching node.
     */
    private String forEachPath;

    /** How to process annotation values (if at all) */
    private final List<ConfigProcessStep> process = new ArrayList<>();

    private final Map<String, String> mapValues = new HashMap<>();

    /** How to index the field (tokenized|untokenized|numeric) */
    private FieldType type = FieldType.TOKENIZED;

    /**
     * When to index the unknownValue: NEVER|MISSING|EMPTY|MISSING_OR_EMPTY
     * (null = use configured default value)
     */
    private UnknownCondition unknownCondition = null;

    /** What to index when unknownCondition is true (null = use configured default value) */
    private String unknownValue = null;

    /** Analyzer to use for this field */
    private String analyzer = "";

    /** How to display the field in the interface (optional) */
    private String displayName = "";

    /** How to describe the field in the interface (optional) */
    private String description = "";

    /** What UI element to show in the interface (optional) */
    private String uiType = "";

    /** Mapping from value to displayValue (optional) */
    private final Map<String, String> displayValues = new HashMap<>();

    /** Order in which to display the values (optional) */
    private final List<String> displayOrder = new ArrayList<>();

    /**
     * Whether to sort multiple value alphabetically or preserve them in document order
     * This reflects on order of values for this field returned by lucene (and by extension, BlackLab and BlackLab server)
     */
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
        cp.setMapValues(mapValues);
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

    public void validate() {
        String t = "metadata field";
        ConfigInputFormat.req(name, t, isForEach() ? "namePath" : "name");
        for (ConfigProcessStep step : process) {
            step.validate();
        }
    }

    public void setForEachPath(String forEachPath) {
        this.forEachPath = forEachPath;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

    public String getName() {
        return name;
    }

    public String getValuePath() {
        return valuePath;
    }

    public String getForEachPath() {
        return forEachPath;
    }

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

    public FieldType getType() {
        return type;
    }

    public void setType(FieldType type) {
        this.type = type;
    }

    public Map<String, String> getDisplayValues() {
        return Collections.unmodifiableMap(displayValues);
    }

    public void addDisplayValue(String value, String displayValue) {
        displayValues.put(value, displayValue);
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

    ProcessingStep processSteps;

    public synchronized ProcessingStep getProcess() {
        if (processSteps == null) {
            processSteps = ProcessingStep.fromConfig(process);
            if (!mapValues.isEmpty()) {
                // Deprecated separate mapValues specified. Translate to a regular processing step.
                processSteps = ProcessingStep.combine(processSteps, new ProcessingStepMapValues(mapValues));
            }
        }
        return processSteps;
    }

    public synchronized void setProcess(List<ConfigProcessStep> process) {
        this.process.clear();
        this.process.addAll(process);
    }

    public void setMapValues(Map<String, String> mapValues) {
        this.mapValues.clear();
        this.mapValues.putAll(mapValues);
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

    public Map<String, String> getMapValues() {
        return mapValues;
    }

}
