package nl.inl.blacklab.indexers.config.process;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.search.BlackLab;

/**
 * Eliminate any duplicate values.
 */
public class ProcessingInstructionSort extends ProcessingInstruction {

    @Override
    public synchronized String getId() {
        return "sort";
    }

    private static final ProcessingStepSort INSTANCE = new ProcessingStepSort();

    @Override
    public ProcessingStep get(PluginParams param) {
        return INSTANCE;
    }

    public ProcessingStep get() {
        return INSTANCE;
    }

    public static class ProcessingStepSort implements ProcessingStep {

        @Override
        public Stream<String> perform(Stream<String> values, Map<String, Collection<String>> metadata) {
            return values.sorted(BlackLab.defaultCollator());
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
            return "sort()";
        }
    }

    @Override
    public boolean isWebSafe() {
        return true;
    }

}
