package nl.inl.blacklab.indexers.config.process;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Strip certain characters from the start and end of the value(s)
 */
public class ProcessingStepIdentity extends ProcessingStep {

    static final ProcessingStepIdentity INSTANCE = new ProcessingStepIdentity();

    private ProcessingStepIdentity() {
    }

    @Override
    public Stream<String> perform(Stream<String> values, Map<String, List<String>> metadata) {
        return values;
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
        return "ident()";
    }

}
