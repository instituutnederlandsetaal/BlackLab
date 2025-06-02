package nl.inl.blacklab.indexers.config.process;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import nl.inl.blacklab.index.DocIndexer;

/**
 * Multiple processing steps (a script).
 */
public class ProcessingStepMultiple extends ProcessingStep {

    /** Steps to apply */
    private final List<ProcessingStep> steps;

    /** Do any of our steps produce multiple values for a single value? */
    private final boolean multi;

    public ProcessingStepMultiple(List<ProcessingStep> steps) {
        this.steps = steps;
        this.multi = steps.stream().anyMatch(ProcessingStep::canProduceMultipleValues);
    }

    public List<ProcessingStep> getSteps() {
        return steps;
    }

    public Stream<String> perform(Stream<String> values, DocIndexer docIndexer) {
        for (ProcessingStep step : steps) {
            values = step.perform(values, docIndexer);
        }
        return values;
    }

    public String performSingle(String value, DocIndexer docIndexer) {
        for (ProcessingStep step : steps) {
            value = step.performSingle(value, docIndexer);
        }
        return value;
    }

    public boolean canProduceMultipleValues() {
        return multi;
    }

    @Override
    public String toString() {
        return "SCRIPT{" + steps.stream().map(ProcessingStep::toString).collect(Collectors.joining("; ")) + "}";
    }
}
