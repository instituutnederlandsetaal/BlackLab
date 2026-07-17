package nl.inl.blacklab.indexers.config.process;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.blacklab.plugins.param.PluginParams;

/**
 * Multiple processing steps (a script).
 */
public class ProcessingInstructionMultiple extends ProcessingInstruction {

    @Override
    public synchronized String localId() {
        return "stmt-block"; // (not actually used in config files)
    }

    @Override
    public ProcessingStep get(PluginParams param) {
        throw new UnsupportedOperationException();
    }

    public static class ProcessingStepMultiple implements ProcessingStep {
        /**
         * Steps to apply
         */
        private final List<ProcessingStep> steps;

        /**
         * Do any of our steps produce multiple values for a single value?
         */
        private final boolean multi;

        public ProcessingStepMultiple(List<ProcessingStep> steps) {
            this.steps = steps;
            this.multi = steps.stream().anyMatch(ProcessingStep::canProduceMultipleValues);
        }

        public List<ProcessingStep> getSteps() {
            return steps;
        }

        @Override
        public Stream<String> perform(Stream<String> values, Map<String, Collection<String>> metadata) {
            for (ProcessingStep step: steps) {
                values = step.perform(values, metadata);
            }
            return values;
        }

        @Override
        public String performSingle(String value, Map<String, Collection<String>> metadata) {
            for (ProcessingStep step: steps) {
                value = step.performSingle(value, metadata);
            }
            return value;
        }

        @Override
        public boolean canProduceMultipleValues() {
            return multi;
        }

        @Override
        public String toString() {
            return "SCRIPT{" + steps.stream().map(ProcessingStep::toString).collect(Collectors.joining("; ")) + "}";
        }
    }

}
