package nl.inl.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.ConsoleAppender.Target;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.status.StatusLogger;

/**
 * Utilities for log4j logging.
 */
public final class LogUtil {

    private LogUtil() {
    }

    /**
     * Will initialize a simple console appender the root logger has no appenders
     * set yet.
     *
     * This gives you a reasonable default if you don't want to create a log4j2.xml
     * (e.g. for simple tools that don't need any fancy logging).
     *
     * @param level level to use for the console appender, if created
     */
    @SuppressWarnings("deprecation")
    public static void setupBasicLoggingConfig(Level level) {
        // (use a config file (e.g. log4j2.xml) or programmatically configure log4j yourself)

        // Temporarily disable status logger to suppress "no config file found" message
        StatusLogger statusLogger = StatusLogger.getLogger();
        Level oldLevel = statusLogger.getLevel();
        statusLogger.setLevel(Level.OFF);

        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        boolean changed = false;
        Configurator.setRootLevel(level);
        if (config.getAppenders().isEmpty()) {
            PatternLayout layout = PatternLayout.createLayout(PatternLayout.SIMPLE_CONVERSION_PATTERN,
                    null, config, null, null, true, false, null, null);
            Appender appender = ConsoleAppender.createAppender(layout, null, Target.SYSTEM_OUT, "Console", true, false,
                    true);
            appender.start();
            config.addAppender(appender);
            AppenderRef ref = AppenderRef.createAppenderRef("Console", null, null);
            AppenderRef[] refs = { ref };
            LoggerConfig loggerConfig = LoggerConfig.createLogger(false, level, "org.apache.logging.log4j",
                    "true", refs, null, config, null);
            loggerConfig.addAppender(appender, null, null);
            config.addLogger("org.apache.logging.log4j", loggerConfig);
            changed = true;
        }

        // Restore status logger level
        statusLogger.setLevel(oldLevel);

        if (changed)
            ctx.updateLoggers();
    }

    /**
     * Will initialize a simple console appender at level WARN if the root logger
     * has no appenders set yet.
     *
     * This gives you a reasonable default if you don't want to create a log4j2.xml
     * (e.g. for simple tools that don't need any fancy logging).
     */
    public static void setupBasicLoggingConfig() {
        setupBasicLoggingConfig(Level.WARN); // Log warnings and up
    }

}
