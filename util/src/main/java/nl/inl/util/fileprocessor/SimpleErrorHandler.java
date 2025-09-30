package nl.inl.util.fileprocessor;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simple error handler that reports errors and can abort or continue.
 */
public class SimpleErrorHandler implements ErrorHandler {
    private static final Logger logger = LogManager.getLogger(SimpleErrorHandler.class);

    private final boolean continueOnError;

    public SimpleErrorHandler(boolean continueOnError) {
        this.continueOnError = continueOnError;
    }

    @Override
    public synchronized boolean errorOccurred(Throwable e, String path, File f) {
        logger.error("Error processing file " + (f != null ? f.toString() : path));
        logger.error(e);
        return continueOnError;
    }
}
