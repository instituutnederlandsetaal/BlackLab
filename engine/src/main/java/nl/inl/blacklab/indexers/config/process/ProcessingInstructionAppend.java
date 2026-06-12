package nl.inl.blacklab.indexers.config.process;

import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.plugins.param.PluginParam;
import nl.inl.blacklab.plugins.param.PluginParams;

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

    private PluginParam parSeparator;

    private PluginParam parPrefix;

    private PluginParam parField;

    private PluginParam parValue;

    @Override
    public synchronized String getId() {
        return "append";
    }

    @Override
    public void initialize() throws PluginException {
        parSeparator = addParam(PString.any("separator"));
        parPrefix = addParam(PString.any("prefix"));
        parField = addParam(PString.identifier("field"));
        parValue = addParam(PString.any("value"));
    }

    @Override
    public ProcessingStep get(PluginParams param) {
        String separator = param.getString(parSeparator, " ");
        String prefix = param.getString(parPrefix, "");
        String field = param.getString(parField, "");
        String fixedValue = param.getString(parValue, "");
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
            this.field = field == null ? "" : field;
            this.fixedValue = fixedValue == null ? "" : fixedValue;
            if (this.field.isEmpty() && this.fixedValue.isEmpty())
                throw new PluginException("Either field or fixedValue must be set");
        }

        @Override
        public String performSingle(String value, Map<String, Collection<String>> metadata) {
            String appendValue;
            if (!field.isEmpty()) {
                // Append value of field
                Collection<String> metadataField = metadata.get(field);
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
            return "append(separator='" + separator + "', " + (!field.isEmpty() ? "field=" + field : "value=" + fixedValue)
                    + ")";
        }
    }

}
