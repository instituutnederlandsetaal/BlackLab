package nl.inl.blacklab.indexers.config.process;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import nl.inl.blacklab.plugins.ProcessingInstruction;

/**
 * Strip certain characters from the start and end of the value(s)
 */
public class ProcessingInstructionIdentity extends ProcessingInstruction {

    @Override
    public synchronized String getId() {
        return "identity";
    }

    public static ProcessingStep getInstance() {
        return ProcessingStepIdentity.INSTANCE;
    }

    @Override
    public ProcessingStep get(Map<String, Object> param) {
        return ProcessingStepIdentity.INSTANCE;
    }

    public static class ProcessingStepIdentity implements ProcessingStep {
        static final ProcessingStepIdentity INSTANCE = new ProcessingStepIdentity();

        private ProcessingStepIdentity() {
        }

        public static ProcessingStep getInstance() {
            return INSTANCE;
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
}
