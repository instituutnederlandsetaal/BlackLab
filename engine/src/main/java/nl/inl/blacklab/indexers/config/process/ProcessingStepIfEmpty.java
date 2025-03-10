package nl.inl.blacklab.indexers.config.process;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.index.DocIndexer;

/**
 * Optionally replace an empty result with a constant value, or the value of a metadata field.
 *
 * <pre>
 * - "field" for the metadata field whose value will be used
 * - "separator" to join the metadata field if it contains multiple values (defaults to ;)
 * - "value" for a constant value ("field" takes precedence if it exists)
 * </pre>
 */
public class ProcessingStepIfEmpty extends ProcessingStep {

    String separator;

    String field;

    String fixedValue;

    public ProcessingStepIfEmpty(String separator, String field, String fixedValue) {
        this.field = field;
        this.separator = separator;
        this.fixedValue = fixedValue;
    }

    public static ProcessingStepIfEmpty fromConfig(Map<String, Object> param) {
        String field = par(param, "field");
        String fixedValue = null;
        if (field == null)
            fixedValue = par(param, "value");
        String separator = par(param, "separator", ";");
        return new ProcessingStepIfEmpty(separator, field, fixedValue);
    }

    public String performSingle(String value, DocIndexer docIndexer) {
        if (value.isEmpty()) {
            String defaultValue;
            if (field != null)
                defaultValue = StringUtils.join(docIndexer.getMetadataField(field), separator);
            else
                defaultValue = fixedValue;
            if (defaultValue != null)
                value = defaultValue;
        }
        return value;
    }

    public boolean canProduceMultipleValues() {
        return false;
    }

    @Override
    public String toString() {
        return "ifempty(separator='" + separator + "', " + (field != null ? "field=" + field : "value=" + fixedValue)
                + ")";
    }
}
