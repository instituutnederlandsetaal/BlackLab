package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionUnique;
import nl.inl.blacklab.indexers.config.process.ProcessingStep;
import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.util.XPathUtil;

/**
 * Configuration for a single annotation (formerly "property") of an annotated field.
 */
public class ConfigAnnotation {

    protected static final Logger logger = LogManager.getLogger(ConfigAnnotation.class);

    /**
     * If null: regular annotation definition. Otherwise, find all nodes matching
     * this XPath, then evaluate name and valuePath as XPaths for each matching
     * node, adding a subannotation value for each. NOTE: forEach is only supported
     * for subannotations. All subannotations need to be declared at the start, however.
     */
    private String forEachPath;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getForEachPath() {
        return forEachPath;
    }

    public void setForEachPath(String forEachPath) {
        this.forEachPath = forEachPath;
    }

    /** Annotation name (or name XPath if forEach) */
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getName() {
        return name;
    }

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

    /** If specified, all other XPath expression are relative to this */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String basePath = null;

    /** Where to find body text (XPath, or column number for tabular formats) */
    private String valuePath;

    /**
     * If valuePath consists only of digits, this is the integer value. Otherwise,
     * it is Integer.MAX_VALUE
     */
    @JsonIgnore
    private int valuePathInt = Integer.MAX_VALUE;

    /** How to process annotation values (if at all) */
    private final List<ConfigProcessStep> process = new ArrayList<>();

    /** "Compiled" process steps */
    @JsonIgnore
    ProcessingStep processSteps = ProcessingInstruction.identity();

    /** How to process namePath (if at all) */
    private final List<ConfigProcessStep> nameProcess = new ArrayList<>();

    /** "Compiled" process steps for namePath */
    @JsonIgnore
    ProcessingStep nameProcessSteps = ProcessingInstruction.identity();

    /**
     * What sensitivity setting to use to index this annotation (optional, default
     * depends on field name)
     */
    private AnnotationSensitivities sensitivity = AnnotationSensitivities.DEFAULT;

    /**
     * Our subannotations. Note that only 1 level of subannotations is processed
     * (i.e. there's no subsubannotations), although we could process more levels if
     * desired.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<ConfigAnnotation> subannotations = new ArrayList<>();
    public void setSubannotations(List<ConfigAnnotation> subannotations) {
        this.subannotations.clear();
        this.subannotationsByName.clear();
        for (ConfigAnnotation a : subannotations) {
            addSubannotation(a);
        }
    }

    /** Our subannotations (except forEach's) by name. */
    @JsonIgnore
    private final Map<String, ConfigAnnotation> subannotationsByName = new LinkedHashMap<>();

    public List<ConfigAnnotation> getSubannotations() {
        return Collections.unmodifiableList(subannotations);
    }

    public ConfigAnnotation getSubannotation(String name) {
        return subannotationsByName.get(name);
    }

    public void addSubannotation(ConfigAnnotation subannotation) {

        if (!subannotation.isForEach()) {
            // Prefix subannotation with parent annotation name
            String name = getName() + AnnotatedFieldNameUtil.SUBANNOTATION_FIELD_PREFIX_SEPARATOR + subannotation.name;
            subannotation.setName(name);
        }

        subannotations.add(subannotation);
        if (!subannotation.isForEach())
            subannotationsByName.put(subannotation.getName(), subannotation);
    }

    /** Should we create a forward index for this annotation? */
    private boolean forwardIndex = true;

    /** What UI element to show in the interface (optional) */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String uiType = "";
    
    /** Should we capture the innerXml of the node instead of the text?
     *
     * @deprecated use serialize(./node()) XPath instead
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @Deprecated
    private boolean captureXml = false;

    /**
     * Is this an internal annotation?
     * BlackLab always generates some internal annotations for every index, these are (usually) not values users are interested in,
     *  so they are marked with "isInternal" in the indexStructure/indexMetadata so clients can ignore them.
     * We also allow users to explicitly mark their own annotations as "internal" annotations.
     * BlackLab itself does not use this flag.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean isInternal = false;

    /** What annotations have we warned about using special default sensitivity? */
    private static final Set<String> warnSensitivity = new HashSet<>();

    public ConfigAnnotation() {
    }

    public ConfigAnnotation(String name, String valuePath, String forEachPath) {
        setName(name);
        setValuePath(valuePath);
        setForEachPath(forEachPath);
    }

    void validate(InputFormatMessages messages, boolean isSubannotation) {
        String id = this.name == null ? (namePath == null ? "UNKNOWN" : namePath) : this.name;
        String t = "annotation " + id;

        if (isForEach())
            messages.mustHave(t, namePath, "namePath");
        else
            messages.mustHave(t, this.name, "name");
        if (!isForEach() && namePath != null)
            messages.error(t + " is not a forEach, may not have namePath");
        if (!isSubannotation) {
            if (isForEach())
                messages.error("top-level " + t + " may not have forEachPath");
        } else {
            if (basePath != null)
                messages.error("sub" + t + " may not have basePath");
            if (!subannotationsByName.isEmpty())
                messages.error("sub " + t + " may not have subannotations");
        }

        for (ConfigAnnotation s : subannotations) {
            s.validate(messages, true);
        }
        for (ConfigProcessStep step : process)
            step.validate(messages);
        for (ConfigProcessStep step : nameProcess)
            step.validate(messages);
        if (captureXml)
            messages.warning("captureXml setting on " + id + " is deprecated, use XPath serialize(./node()) instead");
    }

    public ConfigAnnotation copy() {
        ConfigAnnotation result = new ConfigAnnotation(name, valuePath, forEachPath);
        result.setProcess(process);
        result.setNameProcess(nameProcess);
        result.setDisplayName(displayName);
        result.setDescription(description);
        result.setSensitivity(sensitivity);
        result.setUiType(uiType);
        result.setBasePath(basePath);
        for (ConfigAnnotation a : subannotations) {
            result.addSubannotation(a.copy());
        }
        result.setForwardIndex(forwardIndex);
        result.setCaptureXml(captureXml);
        return result;
    }

    public String getValuePath() {
        return valuePath;
    }

    public void setValue(String value) {
        this.valuePath = XPathUtil.fixedStringToXpath(value);
    }

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
        if (valuePath != null && valuePath.matches("\\d+"))
            valuePathInt = Integer.parseInt(valuePath);
    }

    @JsonIgnore
    public boolean isValuePathInteger() {
        return valuePathInt != Integer.MAX_VALUE;
    }

    public int getValuePathInt() {
        return valuePathInt;
    }

    @JsonIgnore
    public boolean isForEach() {
        return forEachPath != null;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
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

    public AnnotationSensitivities getSensitivity() {
        return sensitivity;
    }

    public void setSensitivity(AnnotationSensitivities sensitivity) {
        this.sensitivity = sensitivity;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ConfigProcessStep> getProcess() {
        return process;
    }

    @JsonIgnore
    public ProcessingStep getCompiledProcessSteps() {
        // We don't synchronize reads, as processSteps is only set once when config is read
        return processSteps;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ConfigProcessStep> getNameProcess() {
        return nameProcess;
    }

    @JsonIgnore
    public ProcessingStep getCompiledNameProcessSteps() {
        // We don't synchronize reads, as processSteps is only set once when config is read
        return nameProcessSteps;
    }

    public void setProcess(List<ConfigProcessStep> process) {
        this.process.clear();
        this.process.addAll(process);

        // "Compile" the process steps
        processSteps = ProcessingInstruction.fromConfig(this.process);

        // If we don't allow duplicate values (we never do, starting from v2),
        // add a unique() step to the end of the processing chain
        processSteps = ProcessingStep.combine(processSteps, new ProcessingInstructionUnique().get());
    }

    public void setNameProcess(List<ConfigProcessStep> nameProcess) {
        this.nameProcess.clear();
        this.nameProcess.addAll(nameProcess);
        nameProcessSteps = ProcessingInstruction.fromConfig(this.nameProcess); // "compile"
    }

    public boolean isForwardIndex() {
        return forwardIndex;
    }

    public void setForwardIndex(boolean forwardIndex) {
        this.forwardIndex = forwardIndex;
    }

    @Deprecated
    public void setCaptureXml(boolean captureXml) {
        this.captureXml = captureXml;
    }

    @Deprecated
    public boolean isCaptureXml() {
        return this.captureXml;
    }
    
    public void setInternal(boolean internal) {
        this.isInternal = internal;
    }

    public boolean getIsInternal() {
        return this.isInternal;
    }

    @Override
    public String toString() {
        return "ConfigAnnotation [name=" + name + "]";
    }

    @JsonIgnore
    public AnnotationSensitivities getSensitivitySetting() {
        AnnotationSensitivities sensitivity = getSensitivity();
        if (sensitivity == AnnotationSensitivities.DEFAULT) {
            sensitivity = AnnotationSensitivities.defaultForAnnotation(name);
        }
        return sensitivity;
    }

    public void setMultipleValues(boolean b) {
        throw new InvalidInputFormatConfig("The 'multipleValues' setting is no longer supported. All annotations support multiple values.");
    }

    public void setAllowDuplicateValues(boolean b) {
        throw new InvalidInputFormatConfig("The 'allowDuplicateValues' setting is no longer supported. Duplicate values are automatically removed.");
    }
}
