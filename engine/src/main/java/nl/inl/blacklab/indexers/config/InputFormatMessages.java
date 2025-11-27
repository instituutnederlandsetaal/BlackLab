package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;

/** Errors and warnings generated while parsing and validating an input format. */
public class InputFormatMessages {

    private final List<String> errors = new ArrayList<>();

    private final List<String> warnings = new ArrayList<>();

    public void error(String message) {
        errors.add(message);
    }

    public void warning(String message) {
        warnings.add(message);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    void mustHave(String owner, String requiredValue, String valueName) {
        if (requiredValue == null || requiredValue.isEmpty())
            error(owner + " must have a " + valueName);
    }

    public void log(Logger logger, String formatIdentifier) {
        for (String error: getErrors())
            logger.error("in input format '{}': {}", formatIdentifier, error);
        for (String warning: getWarnings())
            logger.warn("in input format '{}': {}", formatIdentifier, warning);
    }
}
