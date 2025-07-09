package nl.inl.blacklab.indexers.config;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.Logger;

/** Ensure that certain warnings are only issued once. */
public class WarnOnce {

    protected final Logger logger;

    private final Set<String> reportedWarnings = new HashSet<>();

    public WarnOnce(Logger logger) {
        this.logger = logger;
    }

    /**
     * Don't issue warnings again if they starts with the unique part of the message.
     */
    public synchronized void warn(String uniquePart, String restOfMessage) {
        if (!reportedWarnings.contains(uniquePart)) {
            logger.warn(uniquePart + (restOfMessage == null ? "" : restOfMessage));
            logger.warn("  (above warning is only issued once)");
            reportedWarnings.add(uniquePart);
        }
    }

    /**
     * Don't issue warning again if the message is the same
     */
    public synchronized void warn(String message) {
        warn(message, "");
    }
}
