package nl.inl.blacklab.indexers.config.process;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.indexers.config.ConfigProcessStep;

/** An operation on one or more values during the indexing process.
 *
 * Might be a regex replace, split, etc.
 *
 * These operations take one or more values and produce one or more values;
 * they are used in a sort of flatMap operation, so e.g. a split operation on 3 values
 * might produce a collection of 9 values, not 3 collections of 3 values.
 */
public abstract class ProcessingStep {

    public static ProcessingStep identity() {
        return ProcessingStepIdentity.INSTANCE;
    }

    public static ProcessingStep fromConfig(List<ConfigProcessStep> process) {
        if (process == null || process.isEmpty())
            return ProcessingStepIdentity.INSTANCE;
        if (process.size() == 1)
            return fromConfig(process.get(0));
        return new ProcessingStepMultiple(process.stream()
                .map(ProcessingStep::fromConfig)
                .collect(Collectors.toList()));
    }

    private static ProcessingStep fromConfig(ConfigProcessStep configProcessStep) {
        switch(configProcessStep.getMethod()) {
            case "append":
                return ProcessingStepAppend.fromConfig(configProcessStep.getParam());
            case "chatFormatAgeToMonths":
                return new ProcessingStepChatAge();
            case "concatDate":
                return ProcessingStepConcatDate.fromConfig(configProcessStep.getParam());
            case "default": // (deprecated)
            case "ifempty":
                return ProcessingStepIfEmpty.fromConfig(configProcessStep.getParam());
            case "map":
                return ProcessingStepMapValues.fromConfig(configProcessStep.getParam());
            case "parsePos":
                return ProcessingStepParsePos.fromConfig(configProcessStep.getParam());
            case "replace":
                return ProcessingStepReplace.fromConfig(configProcessStep.getParam());
            case "split":
                return ProcessingStepSplit.fromConfig(configProcessStep.getParam());
            case "strip":
                return ProcessingStepStrip.fromConfig(configProcessStep.getParam());
            case "unique":
                return new ProcessingStepUnique();
            case "sort":
                return new ProcessingStepSort();
            default:
                throw new IllegalArgumentException("Unknown method: " + configProcessStep.getMethod());
        }
    }

    /**
     * Combine two processing steps into one.
     *
     * Will flatten ProcessingStepMultiple objects.
     *
     * @param a first step
     * @param b second step
     * @return combined step
     */
    public static ProcessingStep combine(ProcessingStep a, ProcessingStep b) {
        if (a == null || a instanceof ProcessingStepIdentity)
            return b;
        if (b == null || b instanceof ProcessingStepIdentity)
            return a;
        if (a instanceof ProcessingStepMultiple) {
            List<ProcessingStep> steps = new ArrayList<>(((ProcessingStepMultiple) a).getSteps());
            if (b instanceof ProcessingStepMultiple)
                steps.addAll(((ProcessingStepMultiple) b).getSteps());
            else
                steps.add(b);
            return new ProcessingStepMultiple(steps);
        } else {
            if (b instanceof ProcessingStepMultiple) {
                List<ProcessingStep> steps = new ArrayList<>(((ProcessingStepMultiple) b).getSteps());
                steps.add(0, a);
                return new ProcessingStepMultiple(steps);
            } else {
                return new ProcessingStepMultiple(List.of(a, b));
            }
        }
    }

    public Stream<String> perform(Stream<String> values, DocIndexer docIndexer) {
        return values.map(v -> performSingle(v, docIndexer));
    }

    public static String par(Map<String, Object> param, String key) {
        Object v = param.getOrDefault(key, null);
        return v == null ? null : v.toString();
    }

    public static String par(Map<String, Object> param, String key, String defaultValue) {
        return param.getOrDefault(key, defaultValue).toString();
    }

    public abstract String performSingle(String value, DocIndexer docIndexer);

    /** Can this produce multiple values from a single value?
     *
     * (e.g. split does; strip doesn't)
     */
    public abstract boolean canProduceMultipleValues();

    protected static Pattern getPattern(String regex, String strFlags) {
        int flags = 0;
        if (strFlags.contains("i"))
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        if (strFlags.contains("u"))
            flags |= Pattern.UNICODE_CHARACTER_CLASS;
        return Pattern.compile(regex, flags);
    }

    @Override
    public abstract String toString();
}
