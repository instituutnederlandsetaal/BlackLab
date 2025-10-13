package nl.inl.blacklab.indexers.config.process;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import nl.inl.blacklab.index.DocIndexer;

/**
 * A regular expression replace operation.
 */
public class ProcessingStepReplace extends ProcessingStep {

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
        this.pattern = getPattern(regex, flags);
    }

    public static ProcessingStepReplace fromConfig(Map<String, Object> param) {
        return new ProcessingStepReplace(par(param, "find"), par(param, "replace"),
                par(param, "flags", ""),
                par(param, "keep", "replaced"));
    }

    @Override
    public Stream<String> perform(Stream<String> values, DocIndexer docIndexer) {
        if (keepOriginal) {
            return values.flatMap(v -> Stream.of(v, performSingle(v, docIndexer)));
        } else {
            return values.map(v -> performSingle(v, docIndexer));
        }
    }

    @Override
    public String performSingle(String value, DocIndexer docIndexer) {
        return pattern.matcher(value).replaceAll(replacement);
    }

    @Override
    public boolean canProduceMultipleValues() {
        return keepOriginal;
    }

    @Override
    public String toString() {
        return "replace(regex=" + regex + ", replace=" + replacement + ", flags=" + flags + ", keep=" + (keepOriginal ? "all" : "replaced") + ")";
    }
}
