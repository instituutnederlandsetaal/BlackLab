package nl.inl.blacklab.indexers.config.process;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.index.DocIndexer;

/**
 * Appends a constant value, or the value of a metadata field to the result string.
 *
 * <pre>
 * - "separator" for the separator (defaults to " ")
 * - "field" for the metadata field whose value will be appended
 * - "value" for a constant value ("field" takes precedence if it exists)
 * </pre>
 */
public class ProcessingStepAppend extends ProcessingStep {

    /** Separator for metadata fields with multiple values
     *  (NOTE: in v1, this is also automatically used as the prefix that is appended first)
     *  (default: space)
     */
    String separator;

    /**
     * A string that will be appended before the main append operation.
     * (NEW in v2; v1 used the separator for this)
     * (default: none)
     */
    String prefix;

    /** Name of metadata field to append */
    String field;

    /** If field == null: a fixed string to append */
    String fixedValue;

    public ProcessingStepAppend(String separator, String prefix, String field, String fixedValue) {
        this.separator = separator == null ? " " : separator;
        this.prefix = prefix == null ? "" : prefix;
        this.field = field;
        this.fixedValue = fixedValue == null ? "" : fixedValue;
    }

    public static ProcessingStepAppend fromConfig(Map<String, Object> param) {
        String separator = par(param, "separator", " ");
        String prefix = par(param, "prefix", "");
        String field = par(param, "field");
        String fixedValue = null;
        if (field == null)
            fixedValue = par(param, "value");
        return new ProcessingStepAppend(separator, prefix, field, fixedValue);
    }

    @Override
    public String performSingle(String value, DocIndexer docIndexer) {
        String appendValue;
        if (field != null) {
            // Append value of field
            List<String> metadataField = docIndexer.getMetadataField(field);
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
