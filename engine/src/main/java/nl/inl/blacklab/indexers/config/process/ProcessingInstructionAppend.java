package nl.inl.blacklab.indexers.config.process;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.plugins.ProcessingInstruction;

/**
 * Appends a constant value, or the value of a metadata field to the result string.
 *
 * <pre>
 * - "separator" for the separator (defaults to " ")
 * - "field" for the metadata field whose value will be appended
 * - "value" for a constant value ("field" takes precedence if it exists)
 * </pre>
 */
public class ProcessingInstructionAppend extends ProcessingInstruction {

    @Override
    public synchronized String getId() {
        return "append";
    }

    @Override
    public ProcessingStep get(Map<String, Object> param) {
        String separator = ProcessingStep.par(param, "separator", " ");
        String prefix = ProcessingStep.par(param, "prefix", "");
        String field = ProcessingStep.par(param, "field");
        String fixedValue = null;
        if (field == null)
            fixedValue = ProcessingStep.par(param, "value");
        return new ProcessingStepAppend(separator, prefix, field, fixedValue);
    }

    public static class ProcessingStepAppend implements ProcessingStep {
        /**
         * Separator for metadata fields with multiple values
         * (NOTE: in v1, this is also automatically used as the prefix that is appended first)
         * (default: space)
         */
        String separator;

        /**
         * A string that will be appended before the main append operation.
         * (NEW in v2; v1 used the separator for this)
         * (default: none)
         */
        String prefix;

        /**
         * Name of metadata field to append
         */
        String field;

        /**
         * If field == null: a fixed string to append
         */
        String fixedValue;

        public ProcessingStepAppend(String separator, String prefix, String field, String fixedValue) {
            this.separator = separator == null ? " " : separator;
            this.prefix = prefix == null ? "" : prefix;
            this.field = field;
            this.fixedValue = fixedValue == null ? "" : fixedValue;
        }

        @Override
        public String performSingle(String value, Map<String, List<String>> metadata) {
            String appendValue;
            if (field != null) {
                // Append value of field
                List<String> metadataField = metadata.get(field);
                appendValue = metadataField == null ? "" : StringUtils.join(metadataField, separator);
            } else {
                // Append fixed value
                appendValue = this.fixedValue;
            }
            if (appendValue != null && !appendValue.isEmpty()) {
                if (!value.isEmpty())
                    value += prefix;
                value += appendValue;
            }
            return value;
        }

        @Override
        public boolean canProduceMultipleValues() {
            return false;
        }

        @Override
        public String toString() {
            return "append(separator='" + separator + "', " + (field != null ? "field=" + field : "value=" + fixedValue)
                    + ")";
        }
    }
}
