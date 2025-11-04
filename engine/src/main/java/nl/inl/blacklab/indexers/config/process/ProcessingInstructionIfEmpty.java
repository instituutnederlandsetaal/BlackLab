package nl.inl.blacklab.indexers.config.process;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.plugins.ProcessingInstruction;

/**
 * Optionally replace an empty result with a constant value, or the value of a metadata field.
 *
 * <pre>
 * - "field" for the metadata field whose value will be used
 * - "separator" to join the metadata field if it contains multiple values (defaults to ;)
 * - "value" for a constant value ("field" takes precedence if it exists)
 * </pre>
 */
public class ProcessingInstructionIfEmpty extends ProcessingInstruction {

    @Override
    public synchronized String getId() {
        return "ifEmpty"; // "default" as well (old name)
    }

    @Override
    public ProcessingStep get(Map<String, Object> param) {
        String field = ProcessingStep.par(param, "field");
        String fixedValue = null;
        if (field == null)
            fixedValue = ProcessingStep.par(param, "value");
        String separator = ProcessingStep.par(param, "separator", ";");
        return new ProcessingStepIfEmpty(separator, field, fixedValue);
    }

    public static class ProcessingStepIfEmpty implements ProcessingStep {
        String separator;

        String field;

        String fixedValue;

        public ProcessingStepIfEmpty(String separator, String field, String fixedValue) {
            this.field = field;
            this.separator = separator;
            this.fixedValue = fixedValue;
        }

        @Override
        public String performSingle(String value, Map<String, List<String>> metadata) {
            if (value.isEmpty()) {
                String defaultValue;
                if (field != null)
                    defaultValue = StringUtils.join(metadata.get(field), separator);
                else
                    defaultValue = fixedValue;
                if (defaultValue != null)
                    value = defaultValue;
            }
            return value;
        }

        @Override
        public boolean canProduceMultipleValues() {
            return false;
        }

        @Override
        public String toString() {
            return "ifempty(separator='" + separator + "', " + (field != null ?
                    "field=" + field :
                    "value=" + fixedValue)
                    + ")";
        }
    }
}
