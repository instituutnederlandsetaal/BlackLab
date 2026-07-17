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
 * Optionally replace an empty result with a constant value, or the value of a metadata field.
 *
 * <pre>
 * - "field" for the metadata field whose value will be used
 * - "separator" to join the metadata field if it contains multiple values (defaults to ;)
 * - "value" for a constant value ("field" takes precedence if it exists)
 * </pre>
 */
public class ProcessingInstructionIfEmpty extends ProcessingInstruction {

    private PluginParam parField;

    private PluginParam parValue;

    private PluginParam parSeparator;

    @Override
    public synchronized String localId() {
        return "ifEmpty"; // "default" as well (old name)
    }

    @Override
    public void initialize() throws PluginException {
        parField = addParam(PString.identifier("field"));
        parValue = addParam(PString.any("value"));
        parSeparator = addParam(PString.any("separator"));
    }

    @Override
    public ProcessingStep get(PluginParams param) {
        String field = param.getString(parField, "");
        String fixedValue = "";
        if (field.isEmpty())
            fixedValue = param.getString(parValue, "");
        String separator = param.getString(parSeparator, ";");
        return new ProcessingStepIfEmpty(separator, field, fixedValue);
    }

    public static class ProcessingStepIfEmpty implements ProcessingStep {
        String separator;

        String field;

        String fixedValue;

        public ProcessingStepIfEmpty(String separator, String field, String fixedValue) {
            this.field = field == null ? "" : field;
            this.separator = separator;
            this.fixedValue = fixedValue == null ? "" : fixedValue;
            if (this.field.isEmpty() && this.fixedValue.isEmpty())
                throw new PluginException("Either field or fixedValue must be set");
        }

        @Override
        public String performSingle(String value, Map<String, Collection<String>> metadata) {
            if (value.isEmpty()) {
                value = field.isEmpty() ? fixedValue :
                    StringUtils.join(metadata.get(field), separator);
                if (value == null)
                    value = "";
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
