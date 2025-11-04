package nl.inl.blacklab.plugins;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.indexers.config.ConfigProcessStep;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionIdentity;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionMultiple;
import nl.inl.blacklab.indexers.config.process.ProcessingStep;

/** An operation on one or more values during the indexing process.
 *
 * Might be a regex replace, split, etc.
 *
 * These operations take one or more values and produce one or more values;
 * they are used in a sort of flatMap operation, so e.g. a split operation on 3 values
 * might produce a collection of 9 values, not 3 collections of 3 values.
 */
public abstract class ProcessingInstruction extends Plugin {

    public static ProcessingStep identity() {
        return ProcessingInstructionIdentity.ProcessingStepIdentity.getInstance();
    }

    public static ProcessingStep fromConfig(List<ConfigProcessStep> process) {
        if (process == null || process.isEmpty())
            return ProcessingInstructionIdentity.getInstance();
        if (process.size() == 1)
            return fromConfig(process.get(0));
        return new ProcessingInstructionMultiple.ProcessingStepMultiple(process.stream()
                .map(ProcessingInstruction::fromConfig)
                .collect(Collectors.toList()));
    }

    public static ProcessingStep fromConfig(ConfigProcessStep configProcessStep) {
        String method = configProcessStep.getMethod();
        if (method.equals("default")) // (default was the old name for ifEmpty)
            method = "ifEmpty";
        try {
            ProcessingInstruction pt = PluginManager.type(ProcessingInstruction.class).get(method);
            return pt.get(configProcessStep.getParam());
        } catch (PluginException e){
            throw new IllegalArgumentException("Unknown method: " + method);
        }
    }

    public abstract ProcessingStep get(Map<String, Object> param);

}
