package nl.inl.util.fileprocessor;

import java.io.File;

/**
 * Handles error, and decides whether to continue processing or not.
 */
@FunctionalInterface
public interface ErrorHandler {

    /**
     * Report an error and decide whether to continue or not.
     *
     * @param e    the exception
     * @param path path to the file that the error occurred in. This includes
     *             pathing in archives if the file is inside an archive.
     * @param f    (optional, if known) the file from which the InputStream was built,
     *             or - if the InputStream is a file within an archive - the archive.
     * @return true if we should continue, false to abort
     */
    boolean errorOccurred(Throwable e, String path, File f);
}
