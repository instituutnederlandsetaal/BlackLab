package nl.inl.blacklab.indexers.config.process;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * Strip certain characters from the start and end of the value(s)
 */
public class ProcessingStepStrip extends ProcessingStep {

    /** Feature to extract from PoS (or main part of speech if _) */
    private final String stripChars;

    public ProcessingStepStrip(String stripChars) {
        this.stripChars = stripChars;
    }

    public static ProcessingStepStrip fromConfig(Map<String, Object> param) {
        String stripChars = par(param, "chars", " ");
        return new ProcessingStepStrip(stripChars);
    }

    @Override
    public String performSingle(String value, Map<String, List<String>> metadata) {
        return StringUtils.strip(value, stripChars);
    }

    @Override
    public boolean canProduceMultipleValues() {
        return false;
    }

    @Override
    public String toString() {
        return "strip(chars=" + stripChars + ")";
    }

}
