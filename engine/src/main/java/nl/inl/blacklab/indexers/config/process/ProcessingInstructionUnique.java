package nl.inl.blacklab.indexers.config.process;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import nl.inl.blacklab.plugins.ProcessingInstruction;

/**
 * Eliminate any duplicate values.
 */
public class ProcessingInstructionUnique extends ProcessingInstruction {

    @Override
    public synchronized String getId() {
        return "unique";
    }

    private static final ProcessingStepUnique INSTANCE = new ProcessingStepUnique();

    @Override
    public ProcessingStep get(Map<String, Object> param) {
        return INSTANCE;
    }

    public ProcessingStep get() {
        return INSTANCE;
    }

    public static class ProcessingStepUnique implements ProcessingStep {

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

}
