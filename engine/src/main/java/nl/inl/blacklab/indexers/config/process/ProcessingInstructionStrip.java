package nl.inl.blacklab.indexers.config.process;

import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.plugins.param.PluginParam;
import nl.inl.blacklab.plugins.param.PluginParams;

/**
 * Strip certain characters from the start and end of the value(s)
 */
public class ProcessingInstructionStrip extends ProcessingInstruction {

    private PluginParam parChars;

    @Override
    public synchronized String getId() {
        return "strip";
    }

    @Override
    public void initialize() throws PluginException {
        parChars = addParam(PString.matching("chars", ".+"));
    }

    @Override
    public ProcessingStep get(PluginParams param) {
        return new ProcessingStepStrip(param.getString(parChars, " "));
    }

    public static class ProcessingStepStrip implements ProcessingStep {

        /**
         * Feature to extract from PoS (or main part of speech if _)
         */
        private final String stripChars;

        public ProcessingStepStrip(String stripChars) {
            this.stripChars = stripChars;
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
