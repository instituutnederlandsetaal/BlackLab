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

import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.indexers.config.process.ProcessingStep;
import nl.inl.blacklab.indexers.config.process.ProcessingStepUnique;

/**
 * Configuration for a single annotation (formerly "property") of an annotated field.
 */
public class ConfigAnnotation {

    protected static final Logger logger = LogManager.getLogger(ConfigAnnotation.class);

    /** Annotation name, or forEach (or name XPath, if forEach) */
    private String name;

    /** If specified, all other XPath expression are relative to this */
    private String basePath = null;

    /** Where to find body text */
    private String valuePath;

    /**
     * If valuePath consists only of digits, this is the integer value. Otherwise,
     * it is Integer.MAX_VALUE
     */
    private int valuePathInt = Integer.MAX_VALUE;

    /**
     * If null: regular annotation definition. Otherwise, find all nodes matching
     * this XPath, then evaluate name and valuePath as XPaths for each matching
     * node, adding a subannotation value for each. NOTE: forEach is only supported
     * for subannotations. All subannotations need to be declared at the start, however.
     */
    private String forEachPath;

    /** How to process annotation values (if at all) */
    private final List<ConfigProcessStep> process = new ArrayList<>();

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
    private final List<ConfigAnnotation> subannotations = new ArrayList<>();

    /** Our subannotations (except forEach's) by name. */
    private final Map<String, ConfigAnnotation> subAnnotationsByName = new LinkedHashMap<>();

    /** Should we create a forward index for this annotation? */
    private boolean forwardIndex = true;

    /** How to display the field in the interface (optional) */
    private String displayName = "";

    /** How to describe the field in the interface (optional) */
    private String description = "";

    /** What UI element to show in the interface (optional) */
    private String uiType = "";
    
    /** Should we allow duplicate values at one token position? (if false, performs extra checking and discards duplicates) */
    private boolean allowDuplicateValues = false;
    
    /** Should we capture the innerXml of the node instead of the text */
    private boolean captureXml = false;

    /**
     * Is this an internal annotation?
     * BlackLab always generates some internal annotations for every index, these are (usually) not values users are interested in,
     *  so they are marked with "isInternal" in the indexStructure/indexMetadata so clients can ignore them.
     * We also allow users to explicitly mark their own annotations as "internal" annotations.
     * BlackLab itself does not use this flag.
     */
    private boolean internal = false;

    /** What annotations have we warned about using special default sensitivity? */
    private static final Set<String> warnSensitivity = new HashSet<>();

    public ConfigAnnotation() {
    }

    public ConfigAnnotation(String name, String valuePath, String forEachPath) {
        setName(name);
        setValuePath(valuePath);
        setForEachPath(forEachPath);
    }

    public void validate() {
        String t = "annotation";
        ConfigInputFormat.req(name, t, isForEach() ? "namePath" : "name");
        //ConfigInputFormat.req(valuePath, t, "valuePath");
        for (ConfigAnnotation s : subannotations)
            s.validate();
        for (ConfigProcessStep step : process)
            step.validate();
    }

    public ConfigAnnotation copy() {
        ConfigAnnotation result = new ConfigAnnotation(name, valuePath, forEachPath);
        result.setProcess(process);
        result.setDisplayName(displayName);
        result.setDescription(description);
        result.setSensitivity(sensitivity);
        result.setUiType(uiType);
        result.setBasePath(basePath);
        for (ConfigAnnotation a : subannotations) {
            result.addSubAnnotation(a.copy());
        }
        result.setForwardIndex(forwardIndex);
        result.setAllowDuplicateValues(allowDuplicateValues);
        result.setCaptureXml(captureXml);
        return result;
    }

    public String getName() {
        return name;
    }

    public String getValuePath() {
        return valuePath;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
        if (valuePath != null && valuePath.matches("\\d+"))
            valuePathInt = Integer.parseInt(valuePath);
    }

    public boolean isValuePathInteger() {
        return valuePathInt != Integer.MAX_VALUE;
    }

    public int getValuePathInt() {
        return valuePathInt;
    }

    public List<ConfigAnnotation> getSubAnnotations() {
        return Collections.unmodifiableList(subannotations);
    }

    public ConfigAnnotation getSubAnnotation(String name) {
        return subAnnotationsByName.get(name);
    }

    public void addSubAnnotation(ConfigAnnotation subAnnotation) {
        subannotations.add(subAnnotation);
        if (!subAnnotation.isForEach())
            subAnnotationsByName.put(subAnnotation.getName(), subAnnotation);
    }

    public String getForEachPath() {
        return forEachPath;
    }

    public void setForEachPath(String forEachPath) {
        this.forEachPath = forEachPath;
    }

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

    ProcessingStep processSteps;

    public synchronized ProcessingStep getProcess() {
        if (processSteps == null) {
            processSteps = ProcessingStep.fromConfig(process);
            if (!allowDuplicateValues) {
                // If we don't allow duplicate values (we never do, starting from v2),
                // add a unique() step to the end of the processing chain
                processSteps = ProcessingStep.combine(processSteps, new ProcessingStepUnique());
            }
        }
        return processSteps;
    }

    public synchronized void setProcess(List<ConfigProcessStep> process) {
        this.process.clear();
        this.process.addAll(process);
    }

    public boolean createForwardIndex() {
        return forwardIndex;
    }

    public void setForwardIndex(boolean forwardIndex) {
        this.forwardIndex = forwardIndex;
    }

    public void setAllowDuplicateValues(boolean allowDuplicateValues) {
        this.allowDuplicateValues = allowDuplicateValues;
    }

    public void setCaptureXml(boolean captureXml) {
        this.captureXml = captureXml;
    }
    
    public boolean isCaptureXml() {
        return this.captureXml;
    }
    
    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public boolean isInternal() {
        return this.internal;
    }

    @Override
    public String toString() {
        return "ConfigAnnotation [name=" + name + "]";
    }

    public AnnotationSensitivities getSensitivitySetting() {
        AnnotationSensitivities sensitivity = getSensitivity();
        if (sensitivity == AnnotationSensitivities.DEFAULT) {
            String name = getName();
            sensitivity = AnnotationSensitivities.defaultForAnnotation(name);
        }
        return sensitivity;
    }
}
