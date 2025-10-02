package nl.inl.blacklab.indexers.config.process;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Eliminate any duplicate values.
 */
public class ProcessingStepUnique extends ProcessingStep {

    @Override
    public Stream<String> perform(Stream<String> values, Map<String, List<String>> metadata) {
        return values.distinct();
    }

    @Override
    public String performSingle(String value, Map<String, List<String>> metadata) {
        return value;
    }

    @Override
    public boolean canProduceMultipleValues() {
        return false;
    }

    @Override
    public String toString() {
        return "unique()";
    }

}
