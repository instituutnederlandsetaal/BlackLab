package nl.inl.blacklab.indexers.config.process;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import nl.inl.blacklab.plugins.ProcessingInstruction;

/**
 * A regular expression replace operation.
 */
public class ProcessingInstructionReplace extends ProcessingInstruction {

    @Override
    public synchronized String getId() {
        return "replace";
    }

    @Override
    public ProcessingStep get(Map<String, Object> param) {
        return ProcessingStepReplace.fromConfig(param);
    }

    public static class ProcessingStepReplace implements ProcessingStep {

        private final String regex;

        private final String replacement;

        private final String flags;

        private final boolean keepOriginal;

        private final Pattern pattern;

        public ProcessingStepReplace(String regex, String replacement, String flags, String keep) {
            this.regex = regex;
            this.replacement = replacement;
            this.flags = flags;
            this.keepOriginal = keep.equals("all") || keep.equals("both");
            if (regex == null)
                throw new IllegalArgumentException("replace needs regex");
            if (replacement == null)
                throw new IllegalArgumentException("replace needs replacement");
            this.pattern = ProcessingStep.getPattern(regex, flags);
        }

        public static ProcessingStepReplace fromConfig(Map<String, Object> param) {
            return new ProcessingStepReplace(ProcessingStep.par(param, "find"),
                    ProcessingStep.par(param, "replace"),
                    ProcessingStep.par(param, "flags", ""),
                    ProcessingStep.par(param, "keep", "replaced"));
        }

        @Override
        public Stream<String> perform(Stream<String> values, Map<String, List<String>> metadata) {
            if (keepOriginal) {
                return values.flatMap(v -> Stream.of(v, performSingle(v, metadata)));
            } else {
                return values.map(v -> performSingle(v, metadata));
            }
        }

        @Override
        public String performSingle(String value, Map<String, List<String>> metadata) {
            return pattern.matcher(value).replaceAll(replacement);
        }

        @Override
        public boolean canProduceMultipleValues() {
            return keepOriginal;
        }

        @Override
        public String toString() {
            return "replace(regex=" + regex + ", replace=" + replacement + ", flags=" + flags + ", keep=" + (
                    keepOriginal ?
                            "all" :
                            "replaced") + ")";
        }
    }
}
