package nl.inl.blacklab.indexers.config.process;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.blacklab.plugins.param.PEnum;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.plugins.param.PluginParam;
import nl.inl.blacklab.plugins.param.PluginParams;

/**
 * A regular expression replace operation.
 */
public class ProcessingInstructionReplace extends ProcessingInstruction {

    private PluginParam parFind;

    private PluginParam parReplace;

    private PluginParam parFlags;

    private PluginParam parKeep;

    @Override
    public synchronized String localId() {
        return "replace";
    }

    @Override
    public void initialize() throws PluginException {
        parFind = addParam(PString.matching("find", ".+", true));
        parReplace = addParam(PString.any("replace", true));
        parFlags = addParam(PString.any("flags"));
        parKeep = addParam(PEnum.of("keep", List.of("replaced", "both")));
    }

    @Override
    public ProcessingStep get(PluginParams param) {
        return new ProcessingStepReplace(param.getString(parFind).orElseThrow(),
                param.getString(parReplace).orElseThrow(),
                param.getString(parFlags, ""),
                param.getString(parKeep, "replaced"));
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

        @Override
        public Stream<String> perform(Stream<String> values, Map<String, Collection<String>> metadata) {
            if (keepOriginal) {
                return values.flatMap(v -> Stream.of(v, performSingle(v, metadata)));
            } else {
                return values.map(v -> performSingle(v, metadata));
            }
        }

        @Override
        public String performSingle(String value, Map<String, Collection<String>> metadata) {
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
