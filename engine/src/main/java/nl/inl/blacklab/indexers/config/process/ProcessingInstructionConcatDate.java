package nl.inl.blacklab.indexers.config.process;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.blacklab.plugins.param.PEnum;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.plugins.param.PluginParam;
import nl.inl.blacklab.plugins.param.PluginParams;

/**
 * Concatenate 3 separate date fields into one.
 * E.g.
 * Year: 2000
 * Month: 10
 * Day: 19
 *
 * Result: "20001019"
 */
public class ProcessingInstructionConcatDate extends ProcessingInstruction {

    private PluginParam parYearField;

    private PluginParam parMonthField;

    private PluginParam parDayField;

    private PluginParam parAutofill;

    @Override
    public synchronized String getId() {
        return "concatDate";
    }

    @Override
    public void initialize() throws PluginException {
        parYearField = addParam(PString.identifier("yearField", true));
        parMonthField = addParam(PString.identifier("monthField", true));
        parDayField = addParam(PString.identifier("dayField", true));
        parAutofill = addParam(PEnum.of("autofill", List.of("start", "end")));
    }

    @Override
    public ProcessingStep get(PluginParams param) {
        return new ProcessingStepConcatDate(
                param.getString(parYearField).orElseThrow(),
                param.getString(parMonthField).orElseThrow(),
                param.getString(parDayField).orElseThrow(),
                param.getString(parAutofill, "end"));
    }

    public static class ProcessingStepConcatDate implements ProcessingStep {
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
                throw new IllegalArgumentException(
                        "concatDate requires parameters yearField, monthField and dayField");
        }

        @Override
        public String performSingle(String value, Map<String, Collection<String>> metadata) {
            Integer y, m, d;
            y = getIntFieldValue(metadata, yearField);
            if (y == null)
                return "";
            m = getIntFieldValue(metadata, monthField);
            if (m == null || m > 12 || m < 1)
                m = autoFillStart ? 1 : 12;
            d = getIntFieldValue(metadata, dayField);
            int maxDay = YearMonth.of(y, m).lengthOfMonth();
            if (d == null || d > maxDay || d < 1)
                d = autoFillStart ? 1 : maxDay;

            return value + StringUtils.leftPad(y.toString(), 4, '0') +
                    StringUtils.leftPad(m.toString(), 2, '0') +
                    StringUtils.leftPad(d.toString(), 2, '0');
        }

        private Integer getIntFieldValue(Map<String, Collection<String>> metadata, String fieldName) {
            try {
                Collection<String> metadataField = metadata.get(fieldName);
                if (metadataField == null || metadataField.isEmpty())
                    return null;
                return Integer.parseInt(metadataField.iterator().next());
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

}
