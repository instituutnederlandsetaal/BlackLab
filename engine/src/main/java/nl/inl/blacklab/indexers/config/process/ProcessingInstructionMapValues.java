package nl.inl.blacklab.indexers.config.process;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.blacklab.plugins.param.PStringStringMap;
import nl.inl.blacklab.plugins.param.PluginParam;
import nl.inl.blacklab.plugins.param.PluginParams;

/**
 * Map values according to a mapping table.
 */
public class ProcessingInstructionMapValues extends ProcessingInstruction {

    private PluginParam parTable;

    @Override
    public synchronized String getId() {
        return "map";
    }

    @Override
    public void initialize() throws PluginException {
        parTable = addParam(PStringStringMap.required("table", PStringStringMap.Validator.REASONABLE_LENGTHS));
    }

    @Override
    public ProcessingStepMapValues get(PluginParams param) {
        return new ProcessingStepMapValues(param.getStringStringMap(parTable).orElseThrow());
    }

    public static class ProcessingStepMapValues implements ProcessingStep {

        private final Map<String, String> mapping;

        public ProcessingStepMapValues(Map<String, String> mapping) {
            this.mapping = mapping;
        }

        @Override
        public Stream<String> perform(Stream<String> values, Map<String, Collection<String>> metadata) {
            return values.map(v -> mapping.getOrDefault(v, v));
        }

        @Override
        public String performSingle(String value, Map<String, Collection<String>> metadata) {
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
