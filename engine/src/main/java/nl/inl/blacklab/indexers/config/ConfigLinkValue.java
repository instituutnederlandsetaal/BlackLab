package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.indexers.config.process.ProcessingStep;

/** Configuration for linked document link values. */
public class ConfigLinkValue {

    /** XPath to find value */
    private String valuePath;

    /** Field name to get from Lucene doc */
    private String valueField;

    /** Operations to perform on this value, if any */
    private final List<ConfigProcessStep> process = new ArrayList<>();

    ProcessingStep processSteps = ProcessingStep.identity();

    public ConfigLinkValue() {
    }

    public void validate() {
        if (valuePath == null && valueField == null)
            throw new InvalidInputFormatConfig("Link value must have either valuePath or valueField");
        if (valuePath != null && valueField != null)
            throw new InvalidInputFormatConfig("Link value may only define either valuePath or valueField");
        for (ConfigProcessStep step : process) {
            step.validate();
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

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

    public String getValueField() {
        return valueField;
    }

    public void setValueField(String valueField) {
        this.valueField = valueField;
    }

    public ProcessingStep getProcess() {
        // We don't synchronize reads, as processSteps is only set once when config is read
        return processSteps;
    }

    public synchronized void setProcess(List<ConfigProcessStep> p) {
        process.clear();
        process.addAll(p);
        processSteps = ProcessingStep.fromConfig(process);
    }

    @Override
    public String toString() {
        return "ConfigLinkValue [valuePath=" + valuePath + "]";
    }

}
