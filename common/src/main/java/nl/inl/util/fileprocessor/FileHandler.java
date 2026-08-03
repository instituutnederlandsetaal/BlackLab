package nl.inl.util.fileprocessor;

import java.io.File;

public interface FileHandler {
    default boolean continueIndexing() {
        return true;
    }

    /**
     * Handle a file.
     * <p>
     * This function may be called in multiple threads.
     *
     * @param file file to handle
     * @throws Exception these will be passed to
     *                   {@link ErrorHandler#errorOccurred(Throwable, String, File)}
     */
    void file(FileReference file) throws Exception;
}
