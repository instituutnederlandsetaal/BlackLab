package nl.inl.blacklab.indexers.config.process;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.index.DocIndexer;

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

    public String performSingle(String value, DocIndexer docIndexer) {
        return StringUtils.strip(value, stripChars);
    }

    public boolean canProduceMultipleValues() {
        return false;
    }

    @Override
    public String toString() {
        return "strip(chars=" + stripChars + ")";
    }

}
