package nl.inl.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestFileProcessor {

    public static File testFile;
    public static File testArchive;
    public static File testDirectory;

    // Can't annotate using @BeforeClass, because our @Parameters function runs first
    public static void init() throws URISyntaxException {
        testFile = new File(TestFileProcessor.class.getResource("/TestFileProcessor/TestFile.txt").toURI());
        testArchive = new File(TestFileProcessor.class.getResource("/TestFileProcessor/TestArchive.zip").toURI());
        testDirectory = new File(TestFileProcessor.class.getResource("/TestFileProcessor/TestDirectory").toURI());
    }

    // Input
    @Parameter(0)
    public String fileName;
    @Parameter(1)
    public File inputFile;
    @Parameter(2)
    public boolean useThreads;
    @Parameter(3)
    public boolean recurseSubdirs;
    @Parameter(4)
    public boolean processArchives;

    // Test behavior
    @Parameter(5)
    public boolean shouldTriggerException;

    /*
     * Expected results
     * Only tested when not forcing an exception
     * When exceptions is thrown a hardcoded value is tested:
     * In singlethread test results must sum to 1 (the first result, be it a directory or a file)
     * In multithread tests the results are nondeterministic, so any total can be valid and these are ignore
     */
    @Parameter(6)
    public int expectedDirectories;
    @Parameter(7)
    public int expectedFiles;

    @Parameters(name = "{index} - {0} useThreads:{2} recurse:{3} archives:{4} exception:{5}")
    public static Collection<Object[]> data() throws URISyntaxException {
        init();

        return Arrays.asList(new Object[][] {
                { // Single thread single file
                        testFile.getName(),
                        testFile,
                        false,
                        false,
                        false,

                        false,

                        0,
                        1
                },
                { // Multiple threads single file
                        testFile.getName(),
                        testFile,
                        true,
                        false,
                        false,

                        false,

                        0,
                        1
                },
                { // Single thread non recursive directory
                        testDirectory.getName(),
                        testDirectory,
                        false,
                        false,
                        false,

                        false,

                        1,
                        4
                },
                { // Single thread recursive directory
                        testDirectory.getName(),
                        testDirectory,
                        false,
                        true,
                        false,

                        false,

                        1,
                        5
                },
                { // Single thread recursive directory, throwing exception
                        testDirectory.getName(),
                        testDirectory,
                        false,
                        true,
                        false,

                        true,

                        0, // ignored when throwing
                        0
                },
                { // Multi thread recursive directory
                        testDirectory.getName(),
                        testDirectory,
                        true,
                        true,
                        false,

                        false,

                        1,
                        5
                },
                { // Multi thread recursive directory, throwing exception
                        testDirectory.getName(),
                        testDirectory,
                        true,
                        true,
                        false,

                        true,

                        0, // ignored when throwing
                        0
                },
                { // Single thread archive
                        testArchive.getName(),
                        testArchive,
                        false,
                        false,
                        true,

                        false,

                        0,
                        5
                },
                { // Multi thread archive
                        testArchive.getName(),
                        testArchive,
                        true,
                        false,
                        true,

                        false,

                        0,
                        5
                },
                { // Multi thread archive, throwing exception
                        testArchive.getName(),
                        testArchive,
                        true,
                        false,
                        true,

                        true,

                        0, // ignored when throwing
                        0
                },
        });
    }

    private static class TestException extends Exception {
        // (intentionally left blank)
    }

    // Implementation is synchronized so expected file/directory count is deterministic when throwing exceptions in multithreaded tests
    // If we didn't synchronize then multiple file() or directory() calls may run simultaneously.
    // (also, ArrayList is not threadsafe)
    private static class LoggingFileHandler implements FileProcessor.FileHandler {
        private final boolean triggerException;

        public final List<String> filesReceived = new ArrayList<>();

        public LoggingFileHandler(boolean triggerException) {
            this.triggerException = triggerException;
        }

        @Override
        public synchronized void file(FileReference file) throws Exception {
            this.filesReceived.add(FilenameUtils.getName(file.getPath()));
            if (triggerException)
                throw new TestException();
        }
    }

    private static class LoggingErrorHandler implements FileProcessor.ErrorHandler {
        public Throwable caughtException = null;

        @Override
        public boolean errorOccurred(Throwable e, String path, File f) {
            this.caughtException = e;
            return false;
        }
    }

    @Test
    public void test() {
        LoggingFileHandler fileHandler = new LoggingFileHandler(shouldTriggerException);
        LoggingErrorHandler errorHandler = new LoggingErrorHandler();

        try (FileProcessor proc = new FileProcessor(useThreads ? 2 : 1, recurseSubdirs, processArchives)) {
            proc.setFileHandler(fileHandler);
            proc.setErrorHandler(errorHandler);
            proc.processFileOrDirectory(this.inputFile);
        }

        if (!shouldTriggerException) {
            // Deterministic results
            assertEquals(expectedFiles, fileHandler.filesReceived.size());
        } else if (!useThreads) {
            assertEquals(1, fileHandler.filesReceived.size());
        } // else both throwing and using threads, results are nondeterministic

        assertEquals(shouldTriggerException, errorHandler.caughtException instanceof TestException);
    }
}
