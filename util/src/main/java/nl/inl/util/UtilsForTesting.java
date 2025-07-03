package nl.inl.util;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Misc. testing utilities.
 */
public final class UtilsForTesting {

    /** The underlying files are deleted on close(), if {@link #autoDelete()} is true */
    public static class TestDir implements AutoCloseable {
        File file;
        boolean deleteOnClose = true;

        public TestDir(File f) {
            this.file = f;
        }

        public File file() { return file; }

        public void autoDelete(boolean shouldDeleteOnClose) {
             this.deleteOnClose = shouldDeleteOnClose;
        }

        public boolean autoDelete() { return this.deleteOnClose; }

        /**
         * Delete the underlying files if possible, if an exception occurs,
         * mark the directly and delete it some time in the future when the application runs
         */
        @Override
        public void close() {
            if (this.deleteOnClose)
                deleteDirectory(file);
            else
                createMarkerFile(file);
        }
    }

    private static final File tempDir = new File(System.getProperty("java.io.tmpdir"));

    /** What prefix do all test dirs use? */
    public static final String TEST_DIR_PREFIX = "BlackLabTest_";

    /** What file must be present to allow autoremoving a test dir? */
    public static final String MARKER_FILE_NAME = "REMOVE_TEST_DIR";

    /** How old must marker file be to be automatically removed? (10h) */
    public static final long REMOVE_TEST_DIRS_OLDER_THAN_MS = 10L * 3600 * 1000;


    /*
     * Removes temporary test directories that may be left over from previous test
     * runs because of memory mapping file locking on Windows.
     */
    static {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        long now = System.currentTimeMillis();
        File[] l = tempDir.listFiles((parentDir, name) -> name.startsWith(TEST_DIR_PREFIX));

        if (l != null) {
            // Remove old ContentStore test dirs from temp dir, if possible
            // (may not be possible because of memory mapping lock on Windows;
            //  in this case we just leave the files and continue)
            for (File testDir: l) {
                File markerFile = new File(testDir, MARKER_FILE_NAME);
                if (!markerFile.exists()) {
                    createMarkerFile(testDir);
                } else if (now - markerFile.lastModified() > REMOVE_TEST_DIRS_OLDER_THAN_MS) {
                    deleteDirectory(testDir);
                }
            }
        }
    }

    /** Delete the directory, if it fails, place a marker file. */
    private static void deleteDirectory(File file) {
        try {
            FileUtil.deleteTree(file);
        } catch (Exception e) {
            createMarkerFile(file);
        }
    }

    private UtilsForTesting() {
    }

    /**
     * Create a temporary directory for BlackLab testing. A GUID is used to avoid
     * collisions. When finished with the TestDir, don't forget to close() it to delete it!
     * Note that because of memory mapping and file locking issues, temp
     * dirs may hang around even after closing, they will be detected and deleted eventually, however.
     *
     * @param name descriptive name to be used in the temporary dir (useful while
     *            debugging)
     * @return the newly created temp dir.
     */
    public static synchronized TestDir createBlackLabTestDir(String name) {
        File testDir = new File(tempDir, TEST_DIR_PREFIX + name + "_" + UUID.randomUUID());
        if (!testDir.mkdir())
            throw new RuntimeException("Unable to create test dir: " + testDir);

        createMarkerFile(testDir);

        return new TestDir(testDir);
    }

    private static void createMarkerFile(File directory) {
        File markerFile = new File(directory, MARKER_FILE_NAME);
        try {
            if (!markerFile.createNewFile())
                throw new RuntimeException("Unable to create marker file: " + markerFile);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create marker file: " + markerFile, e);
        }
    }
}
