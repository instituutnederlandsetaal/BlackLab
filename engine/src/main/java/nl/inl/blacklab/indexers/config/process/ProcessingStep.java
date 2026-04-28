package nl.inl.blacklab.indexers.config.process;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public interface ProcessingStep {

    /**
     * Combine two processing steps into one.
     * Will flatten ProcessingStepMultiple objects.
     *
     * @param a first step
     * @param b second step
     * @return combined step
     */
    static ProcessingStep combine(ProcessingStep a, ProcessingStep b) {
        if (a == null || a instanceof ProcessingInstructionIdentity)
            return b;
        if (b == null || b instanceof ProcessingInstructionIdentity)
            return a;
        if (a instanceof ProcessingInstructionMultiple.ProcessingStepMultiple a1) {
            List<ProcessingStep> steps = new ArrayList<>(a1.getSteps());
            if (b instanceof ProcessingInstructionMultiple.ProcessingStepMultiple b1)
                steps.addAll(b1.getSteps());
            else
                steps.add(b);
            return new ProcessingInstructionMultiple.ProcessingStepMultiple(steps);
        } else {
            if (b instanceof ProcessingInstructionMultiple.ProcessingStepMultiple b1) {
                List<ProcessingStep> steps = new ArrayList<>(b1.getSteps());
                steps.add(0, a);
                return new ProcessingInstructionMultiple.ProcessingStepMultiple(steps);
            } else {
                return new ProcessingInstructionMultiple.ProcessingStepMultiple(List.of(a, b));
            }
        }
    }

//    static String par(Map<String, Object> param, String key) {
//        Object v = param.getOrDefault(key, null);
//        return v == null ? null : v.toString();
//    }

    static Pattern getPattern(String regex, String strFlags) {
        int flags = 0;
        if (strFlags.contains("i"))
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        if (strFlags.contains("u"))
            flags |= Pattern.UNICODE_CHARACTER_CLASS;
        return Pattern.compile(regex, flags);
    }

//    static String par(PluginParams param, String key, String defaultValue) {
//        return param.getString(key, defaultValue);
//    }

    default Stream<String> perform(Stream<String> values, Map<String, Collection<String>> metadata) {
        return values.map(v -> performSingle(v, metadata));
    }

    String performSingle(String value, Map<String, Collection<String>> metadata);

    /**
     * Can this produce multiple values from a single value?
     * (e.g. split does; strip doesn't)
     */
    boolean canProduceMultipleValues();
}
