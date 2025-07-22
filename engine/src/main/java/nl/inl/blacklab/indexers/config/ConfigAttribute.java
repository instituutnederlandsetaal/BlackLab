package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.indexers.config.process.ProcessingStep;

/**
 * Configuration for attributes to index using XPath
 */
public class ConfigAttribute {
    /**
     * Attribute name
     */
    private String name;

    /**
     * Exclude this attribute?
     */
    private boolean exclude = false;

    /**
     * XPath to get attribute's value, or null if this attribute is present on the tag.
     */
    private String valuePath;

    /**
     * How to process annotation values (if at all)
     */
    private final List<ConfigProcessStep> process = new ArrayList<>();

    public ConfigAttribute() {
        // Default constructor for deserialization
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValuePath() {
        return valuePath;
    }

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

    public void setExclude(boolean exclude) {
        this.exclude = exclude;
    }

    public boolean isExclude() {
        return exclude;
    }

    ProcessingStep processSteps;

    public synchronized ProcessingStep getProcess() {
        if (processSteps == null) {
            processSteps = ProcessingStep.fromConfig(process);
        }
        return processSteps;
    }

    public synchronized void setProcess(List<ConfigProcessStep> process) {
        this.process.clear();
        this.process.addAll(process);
    }

    public void validate() {
        ConfigInputFormat.req(name, "extra attribute", "name");
        ConfigInputFormat.req(valuePath, "extra attribute", "valuePath");
        for (ConfigProcessStep step: process) {
            step.validate();
        }
    }

    /**
     * Is this a nameless rule that simply says "exclude any attribute that isn't explicitly included?"
     */
    public boolean isDefaultExclude() {
        return exclude && name == null;
    }
}
