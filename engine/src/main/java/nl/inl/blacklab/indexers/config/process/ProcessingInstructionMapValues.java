package nl.inl.blacklab.indexers.config.process;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import nl.inl.blacklab.plugins.ProcessingInstruction;

/**
 * Map values according to a mapping table.
 */
public class ProcessingInstructionMapValues extends ProcessingInstruction {

    @Override
    public synchronized String getId() {
        return "map";
    }

    @Override
    public ProcessingStep get(Map<String, Object> param) {
        return ProcessingStepMapValues.fromConfig(param);
    }

    public static class ProcessingStepMapValues implements ProcessingStep {

        private final Map<String, String> mapping;

        public ProcessingStepMapValues(Map<String, String> mapping) {
            this.mapping = mapping;
        }

        public static ProcessingStepMapValues fromConfig(Map<String, Object> param) {
            Map<String, String> mapping = param.containsKey("table") ?
                    (Map<String, String>) param.get("table") :
                    Collections.emptyMap();
            return new ProcessingStepMapValues(mapping);
        }

        @Override
        public Stream<String> perform(Stream<String> values, Map<String, List<String>> metadata) {
            return values.map(v -> mapping.getOrDefault(v, v));
        }

        @Override
        public String performSingle(String value, Map<String, List<String>> metadata) {
            return mapping.getOrDefault(value, value);
        }

        @Override
        public boolean canProduceMultipleValues() {
            return false;
        }

        @Override
        public String toString() {
            return "map(<mapping with " + mapping.size() + " entries>)";
        }
    }
}
