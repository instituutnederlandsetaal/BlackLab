package nl.inl.blacklab.indexers.config.process;

import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.plugins.ProcessingInstruction;

/**
 * Strip certain characters from the start and end of the value(s)
 */
public class ProcessingInstructionStrip extends ProcessingInstruction {

    @Override
    public synchronized String getId() {
        return "strip";
    }

    @Override
    public ProcessingStep get(Map<String, Object> param) {
        return ProcessingStepStrip.fromConfig(param);
    }

    public static class ProcessingStepStrip implements ProcessingStep {

        /**
         * Feature to extract from PoS (or main part of speech if _)
         */
        private final String stripChars;

        public ProcessingStepStrip(String stripChars) {
            this.stripChars = stripChars;
        }

        public static ProcessingStepStrip fromConfig(Map<String, Object> param) {
            String stripChars = ProcessingStep.par(param, "chars", " ");
            return new ProcessingStepStrip(stripChars);
        }

        @Override
        public String performSingle(String value, Map<String, Collection<String>> metadata) {
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

}
