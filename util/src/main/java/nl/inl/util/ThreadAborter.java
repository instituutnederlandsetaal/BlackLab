package nl.inl.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Utility class for checking {@link Thread#isInterrupted()} and throwing InterruptedException. */
public class ThreadAborter {
    private static final Logger logger = LogManager.getLogger(ThreadAborter.class);

    private ThreadAborter() {
    }

    /**
     * If the thread we're controlling is supposed to be aborted, throw an exception.
     *
     * @throws InterruptedException if thread was interrupted from elsewhere (e.g. load manager)
     */
    public static void checkAbort() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            logger.debug("Thread was interrupted, throw exception");
            throw new InterruptedException("Operation aborted");
        }
    }

}
