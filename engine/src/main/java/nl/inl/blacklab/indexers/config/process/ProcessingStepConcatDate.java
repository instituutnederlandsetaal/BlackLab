package nl.inl.blacklab.indexers.config.process;

import java.time.YearMonth;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.index.DocIndexer;

/**
 * Concatenate 3 separate date fields into one.
 * E.g.
 * Year: 2000
 * Month: 10
 * Day: 19
 *
 * Result: "20001019"
 */
public class ProcessingStepConcatDate extends ProcessingStep {

    private final String yearField;

    private final String monthField;

    private final String dayField;

    private final boolean autoFillStart;

    public ProcessingStepConcatDate(String yearField, String monthField, String dayField, String autofillMode) {
        this.yearField = yearField;
        this.monthField = monthField;
        this.dayField = dayField;
        this.autoFillStart = autofillMode.equalsIgnoreCase("start");
        if (this.yearField == null || this.monthField == null || this.dayField == null)
            throw new IllegalArgumentException("concatDate needs parameters yearField, monthField, dayField, and autofill ('start' or 'end')");
    }

    public static ProcessingStepConcatDate fromConfig(Map<String, Object> param) {
        return new ProcessingStepConcatDate(
            par(param, "yearField"),
            par(param, "monthField"),
            par(param, "dayField"),
            par(param,"autofill", "end"));
    }

    @Override
    public String performSingle(String value, DocIndexer docIndexer) {
        Integer y, m, d;
        y = getIntFieldValue(docIndexer, yearField);
        if (y == null)
            return "";
        m = getIntFieldValue(docIndexer, monthField);
        if (m == null || m > 12 || m < 1)
            m = autoFillStart ? 1 : 12;
        d = getIntFieldValue(docIndexer, dayField);
        int maxDay = YearMonth.of(y, m).lengthOfMonth();
        if (d == null || d > maxDay || d < 1)
            d = autoFillStart ? 1 : maxDay;

        return value + StringUtils.leftPad(y.toString(), 4, '0') +
                StringUtils.leftPad(m.toString(), 2, '0') +
                StringUtils.leftPad(d.toString(), 2, '0');
    }

    private Integer getIntFieldValue(DocIndexer docIndexer, String fieldName) {
        try {
            return Integer.parseInt(docIndexer.getMetadataField(fieldName).get(0));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean canProduceMultipleValues() {
        return false;
    }

    @Override
    public String toString() {
        return "concatDate(yearField=" + yearField + ", monthField=" + monthField + ", dayField=" + dayField
                + ", autofillMode=" + (autoFillStart ? "start" : "end") + ")";
    }
}
