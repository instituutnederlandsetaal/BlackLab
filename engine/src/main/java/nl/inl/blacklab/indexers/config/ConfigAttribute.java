package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import nl.inl.blacklab.indexers.config.process.ProcessingStep;
import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.util.XPathUtil;

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

    @JsonIgnore
    ProcessingStep processSteps = ProcessingInstruction.identity();

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

    public void setValue(String value) {
        this.valuePath = XPathUtil.fixedStringToXpath(value);
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

    void validate(InputFormatMessages messages) {
        messages.mustHave("extra attribute", name, "name");
        if (!exclude)
            messages.mustHave("extra attribute", valuePath, "valuePath");
        for (ConfigProcessStep step: process) {
            step.validate(messages);
        }
    }

    /**
     * Is this a nameless rule that simply says "exclude any attribute that isn't explicitly included?"
     */
    public boolean isDefaultExclude() {
        return exclude && name == null;
    }
}
