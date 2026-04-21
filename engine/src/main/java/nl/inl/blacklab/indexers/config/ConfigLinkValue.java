package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import nl.inl.blacklab.indexers.config.process.ProcessingStep;
import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.util.XPathUtil;

/** Configuration for linked document link values. */
public class ConfigLinkValue {

    /** XPath to find value */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String valuePath;

    /** Field name to get from Lucene doc */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String valueField;

    /** Operations to perform on this value, if any */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<ConfigProcessStep> process = new ArrayList<>();

    @JsonIgnore
    ProcessingStep processSteps = ProcessingInstruction.identity();

    public ConfigLinkValue() {
    }

    void validate(InputFormatMessages messages) {
        if (valuePath == null && valueField == null)
            messages.error("Link value must have either valuePath or valueField");
        if (valuePath != null && valueField != null)
            messages.error("Link value may only define either valuePath or valueField");
        for (ConfigProcessStep step : process) {
            step.validate(messages);
        }
    }

    public ConfigLinkValue copy() {
        ConfigLinkValue cp = new ConfigLinkValue();
        cp.setValuePath(valuePath);
        cp.setValueField(valueField);
        cp.process.addAll(process);
        return cp;
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

    public String getValueField() {
        return valueField;
    }

    public void setValueField(String valueField) {
        this.valueField = valueField;
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

    public void setProcess(List<ConfigProcessStep> p) {
        process.clear();
        process.addAll(p);
        processSteps = ProcessingInstruction.fromConfig(process);
    }

    @Override
    public String toString() {
        return "ConfigLinkValue [valuePath=" + valuePath + "]";
    }

}
