package nl.inl.blacklab.indexers.config.process;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.plugins.param.PluginParam;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.util.StringUtil;

/**
 * Parse a part of speech and features expression of the form "A(b=c,d=e)".
 */
public class ProcessingInstructionParsePos extends ProcessingInstruction {

    private PluginParam parField;

    @Override
    public synchronized String getName() {
        return "parsePos";
    }

    @Override
    public void initialize() throws PluginException {
        parField = addParam(PString.identifier("field"));
    }

    @Override
    public ProcessingStep get(PluginParams param) {
        String field = param.getString( parField, "_");
        return new ProcessingStepParsePos(field);
    }

    public static class ProcessingStepParsePos implements ProcessingStep {

        static final Pattern MAIN_POS_PATTERN = Pattern.compile("^([^(]+)(\\s*\\(.*\\))?$");

        static final Pattern FEATURE_PATTERN = Pattern.compile("^[^(]+(\\s*\\((.*)\\))?$");

        /**
         * Feature to extract from PoS (or main part of speech if _)
         */
        private final String featureName;

        public static ProcessingStepParsePos feature(String featureName) {
            return new ProcessingStepParsePos(featureName);
        }

        public ProcessingStepParsePos(String featureName) {
            this.featureName = featureName;
        }

        @Override
        public String performSingle(String value, Map<String, Collection<String>> metadata) {
            // Trim character/string from beginning and end
            value = StringUtil.trimWhitespace(value);
            if (featureName.equals("_")) {
                //  Get main pos: A(b=c,d=e) -> A
                return MAIN_POS_PATTERN.matcher(value).replaceAll("$1");
            } else {
                //  Get feature: A(b=c,d=e) -> e  (if field == d)
                String featuresString = FEATURE_PATTERN.matcher(value).replaceAll("$2");
                return Arrays.stream(featuresString.split(","))
                        .map(feat -> feat.split("="))
                        .filter(featParts -> StringUtil.trimWhitespace(featParts[0]).equals(featureName))
                        .map(featParts -> StringUtil.trimWhitespace(featParts[1]))
                        .findFirst()
                        .orElse("");
            }
        }

        @Override
        public boolean canProduceMultipleValues() {
            return false;
        }

        @Override
        public String toString() {
            return "parsePos(field=" + featureName + ")";
        }
    }

}
