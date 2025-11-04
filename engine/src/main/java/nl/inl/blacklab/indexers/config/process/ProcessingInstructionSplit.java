package nl.inl.blacklab.indexers.config.process;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import nl.inl.blacklab.plugins.ProcessingInstruction;

/**
 * A regular expression replace operation.
 */
public class ProcessingInstructionSplit extends ProcessingInstruction {

    @Override
    public synchronized String getId() {
        return "split";
    }

    @Override
    public ProcessingStep get(Map<String, Object> param) {
        return ProcessingStepSplit.fromConfig(param);
    }

    public static class ProcessingStepSplit implements ProcessingStep {

        private final String separator;

        private final String flags;

        private final String strKeep;

        private boolean keepOriginal;

        private int keepIndex = -1;

        private final Pattern pattern;

        public ProcessingStepSplit(String separator, String flags, String keep) {
            this.separator = separator;
            this.flags = flags;
            this.strKeep = keep;
            interpretKeep();
            if (separator == null)
                throw new IllegalArgumentException("split needs separator");
            this.pattern = ProcessingStep.getPattern(separator, flags);
        }

        public static ProcessingStepSplit fromConfig(Map<String, Object> param) {
            return new ProcessingStepSplit(ProcessingStep.par(param, "separator", ";"),
                    ProcessingStep.par(param, "flags", ""),
                    ProcessingStep.par(param, "keep", "all").toLowerCase());
        }

        private void interpretKeep() {
            String keep = strKeep.toLowerCase();
            this.keepOriginal = keep.equals("both");
            if (!keep.equals("all") && !keep.equals("both")) {
                int i = -1;
                try {
                    i = Integer.parseInt(keep);
                    if (i >= 0)
                        keepIndex = i;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid value for keep: " + keep);
                }
            }
        }

        @Override
        public Stream<String> perform(Stream<String> values, Map<String, List<String>> metadata) {
            return values.flatMap(value -> {
                String[] parts = pattern.split(value, -1);
                if (!keepAllParts()) {
                    if (keepIndex < parts.length) {
                        String selected = parts[keepIndex];
                        return keepOriginal ? Stream.of(value, selected) : Stream.of(selected);
                    }
                    return keepOriginal ? Stream.of(value) : Stream.empty();
                }
                return keepOriginal ?
                        Stream.concat(Stream.of(value), Stream.of(parts)) :
                        Arrays.stream(parts);
            });
        }

        @Override
        public String performSingle(String value, Map<String, List<String>> metadata) {
            String[] parts = pattern.split(value, -1);
            if (keepOriginal) {
                // dumb, but logical (return the first value we would return for multiple)
                // (shouldn't matter anymore because we only use performSingle if we know in advance
                //  we won't have multiple values)
                return value;
            }
            if (!keepAllParts()) {
                if (keepIndex < parts.length) {
                    return parts[keepIndex];
                }
                return "";
            }
            return parts.length > 0 ? parts[0] : "";
        }

        @Override
        public boolean canProduceMultipleValues() {
            return keepAllParts() || keepOriginal;
        }

        private boolean keepAllParts() {
            return keepIndex < 0;
        }

        @Override
        public String toString() {
            return "split(separator=" + separator + ", flags=" + flags + ", keep=" + strKeep + ")";
        }
    }
}
