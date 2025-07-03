package nl.inl.blacklab.indexers.config.process;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import nl.inl.blacklab.index.DocIndexer;

/**
 * Map values according to a mapping table.
 */
public class ProcessingStepMapValues extends ProcessingStep {

    private final Map<String, String> mapping;

    public ProcessingStepMapValues(Map<String, String> mapping) {
        this.mapping = mapping;
    }

    public static ProcessingStep fromConfig(Map<String, Object> param) {
        Map<String, String> mapping = param.containsKey("table") ?
                (Map<String, String>) param.get("table") :
                Collections.emptyMap();
        return new ProcessingStepMapValues(mapping);
    }

    @Override
    public Stream<String> perform(Stream<String> values, DocIndexer docIndexer) {
        return values.map(v -> mapping.getOrDefault(v, v));
    }

    @Override
    public String performSingle(String value, DocIndexer docIndexer) {
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
