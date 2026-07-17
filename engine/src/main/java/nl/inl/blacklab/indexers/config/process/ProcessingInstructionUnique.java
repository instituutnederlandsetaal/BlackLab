package nl.inl.blacklab.indexers.config.process;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.blacklab.plugins.param.PluginParams;

/**
 * Eliminate any duplicate values.
 */
public class ProcessingInstructionUnique extends ProcessingInstruction {

    @Override
    public synchronized String localId() {
        return "unique";
    }

    private static final ProcessingStepUnique INSTANCE = new ProcessingStepUnique();

    @Override
    public ProcessingStep get(PluginParams param) {
        return INSTANCE;
    }

    public ProcessingStep get() {
        return INSTANCE;
    }

    public static class ProcessingStepUnique implements ProcessingStep {

        @Override
        public Stream<String> perform(Stream<String> values, Map<String, Collection<String>> metadata) {
            return values.distinct();
        }

        @Override
        public String performSingle(String value, Map<String, Collection<String>> metadata) {
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
