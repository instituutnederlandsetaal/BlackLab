package nl.inl.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * Utilities for working with files
 */
public class FileUtil {

    protected static final Logger logger = LogManager.getLogger(FileUtil.class);

    /**
     * The default encoding for opening files.
     */
    private static final Charset DEFAULT_ENCODING = StandardCharsets.UTF_8;

    /**
     * Sorts File objects alphabetically, case-insensitively, subdirectories first.
     * Used by listFilesSorted().
     */
    final public static Comparator<File> LIST_FILES_COMPARATOR = (a, b) -> {
        int ad = a.isDirectory() ? 0 : 1;
        int bd = b.isDirectory() ? 0 : 1;
        return ad != bd ? (ad - bd) : a.getName().compareToIgnoreCase(b.getName());
    };

    /**
     * Opens a file for writing in the default encoding.
     *
     * Wraps the Writer in a BufferedWriter and PrintWriter for efficient and
     * convenient access.
     *
     * @param file the file to open
     * @return write interface into the file
     */
    public static PrintWriter openForWriting(File file) throws FileNotFoundException {
        return openForWriting(file, DEFAULT_ENCODING);
    }

    /**
     * Opens a file for writing.
     *
     * Wraps the Writer in a BufferedWriter and PrintWriter for efficient and
     * convenient access.
     *
     * @param file the file to open
     * @param encoding the encoding to use, e.g. "utf-8"
     * @return write interface into the file
     */
    public static PrintWriter openForWriting(File file, Charset encoding) throws FileNotFoundException {
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding)));
    }

    /**
     * Opens a file for reading, with the default encoding.
     *
     * Wraps the Reader in a BufferedReader for efficient and convenient access.
     *
     * @param file the file to open
     * @return read interface into the file
     */
    public static BufferedReader openForReading(File file) throws FileNotFoundException {
        return openForReading(file, DEFAULT_ENCODING);
    }

    /**
     * Opens a file for reading, with the default encoding.
     *
     * Wraps the Reader in a BufferedReader for efficient and convenient access.
     *
     * @param file the file to open
     * @param encoding the encoding to use, e.g. "utf-8"
     * @return read interface into the file
     */
    public static BufferedReader openForReading(File file, Charset encoding) throws FileNotFoundException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
    }

    /**
     * Read a file into a list of lines
     *
     * @param inputFile the file to read
     * @return list of lines
     */
    public static List<String> readLines(File inputFile) {
        return readLines(inputFile, DEFAULT_ENCODING);
    }

    /**
     * Read a file into a list of lines
     *
     * @param inputFile the file to read
     * @param encoding the encoding to use, e.g. "utf-8"
     * @return list of lines
     */
    public static List<String> readLines(File inputFile, Charset encoding) {
        try {
            List<String> result = new ArrayList<>();
            try (BufferedReader in = openForReading(inputFile, encoding)) {
                String line;
                while ((line = in.readLine()) != null) {
                    result.add(StringUtil.trimWhitespace(line));
                }
            }
            return result;
        } catch (Exception e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    /**
     * Recursively delete an entire directory tree.
     *
     * @param dir directory tree to delete.
     */
    public static void deleteTree(File dir) {
        deleteTree(dir, false);
    }

    /**
     * Recursively delete an entire directory tree.
     *
     * @param dir directory tree to delete.
     * @param throwOnError if true, throw an exception if a file could not be deleted.
     */
    public static void deleteTree(File dir, boolean throwOnError) {
        // Recursively delete this temp dir
        processTree(dir, new FileTask() {
            @Override
            public void process(File f) {
                if (!f.delete() && throwOnError) {
                    if (f.isDirectory())
                        throw new RuntimeException("Could not delete directory: " + f);
                    else
                        throw new RuntimeException("Could not delete file: " + f);
                }
            }
        });
        if (!dir.delete() && throwOnError)
            throw new RuntimeException("Could not delete directory: " + dir);
    }

    /** Check if a file is in a given directory or one of its subdirectories.
     *
     * @param file the file to check
     * @param directory the directory to check against
     * @return true if the file is in the directory or one of its subdirectories, false otherwise
     */
    public static boolean isFileInDirectory(File file, File directory) {
        return file.toPath().normalize().startsWith(directory.toPath().normalize());
    }

    /**
     * Create a temporary file in the java.io.tmpdir and ensure it is
     * actually in that directory.
     *
     * @param fileName (part of) name of temp file
     * @return the temporary file
     * @throws IOException if an error occurs while creating the file
     */
    public static File createTempFileSafe(String fileName) throws IOException {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        SecureRandom random = new SecureRandom();
        File file = new File(tempDir, random.nextInt(Integer.MAX_VALUE) + "_" + fileName);
        boolean ok = isFileInDirectory(file, tempDir);
        if (!ok)
            throw new BlackLabRuntimeException("Uploaded file not in temp dir: " + file.getName());
        if (!file.createNewFile())
            throw new BlackLabRuntimeException("Could not create temp file: " + file.getName());
        return file;
    }

    /**
     * A task to execute on a file. Used by processTree().
     */
    public static abstract class FileTask {
        /**
         * Execute the task on this file.
         *
         * @param f the file to process
         */
        public abstract void process(File f);
    }

    /**
     * Perform an operation on all files in a tree.
     *
     * Sorts the files alphabetically, with directories first, so they are always
     * processed in the same order.
     *
     * @param root the directory to start in (all subdirs are processed)
     * @param task the task to execute for every file
     */
    public static void processTree(File root, FileTask task) {
        if (!root.isDirectory())
            throw new IllegalArgumentException("FileUtil.processTree: must be called with a directory! "
                    + root);
        for (File f : listFilesSorted(root)) {
            if (f.isFile())
                task.process(f);
            else if (f.isDirectory()) {
                processTree(f, task);
                task.process(f);
            }
        }
    }

    /**
     * Perform an operation on some files in a tree.
     *
     * Sorts the files alphabetically, with directories first, so they are always
     * processed in the same order.
     *
     * @param dir the directory to start in
     * @param glob which files to process (e.g. "*.xml")
     * @param recurseSubdirs whether or not to process subdirectories
     * @param task the task to execute for every file
     */
    public static void processTree(File dir, String glob, boolean recurseSubdirs, FileTask task) {
        Pattern pattGlob = Pattern.compile(FileUtil.globToRegex(glob));
        for (File file : listFilesSorted(dir)) {
            if (file.isDirectory()) {
                // Process subdir?
                if (recurseSubdirs)
                    processTree(file, glob, recurseSubdirs, task);
            } else if (file.isFile()) {
                // Regular file; does it match our glob expression?
                Matcher m = pattGlob.matcher(file.getName());
                if (m.matches()) {
                    task.process(file);
                }
            }
        }
    }

    /**
     * Find a file, searching several directories and trying several extensions.
     *
     * Searches the first directory for the file name with each of the extensions,
     * then the second directory, etc.
     *
     * Will only find a file if it is really inside the directory, so e.g. passing
     * <code>../../etc/passwd</code> won't work.
     *
     * @param dirsToSearch directories to search for the file
     * @param pathToFile file name or path to the file (without extension if
     *            extensions != null)
     * @param extensions extensions to try, or null if the extension is already in
     *            pathToFile
     * @return the file if found or null if not found
     */
    public static File findFile(List<File> dirsToSearch, String pathToFile, List<String> extensions) {
        // Read JSON or YAML config file from any of the specified directories.
        File configFile;
        int i = 0;
        for (File dir : dirsToSearch) {
            if (dir == null)
                throw new IllegalArgumentException("dirsToSearch[" + i + "] == null!");
            i++;
            if (extensions == null) {
                configFile = new File(dir, pathToFile);
                boolean fileExists = configFile.exists();
                boolean reallyInsideDir = configFile.getAbsolutePath().startsWith(dir.getAbsolutePath());
                if (!fileExists)
                    logger.trace("Configfile not found: " + configFile);
                else if (!reallyInsideDir)
                    logger.debug("Configfile found but not inside dir: " + configFile);
                if (fileExists && reallyInsideDir)
                    return configFile;
            } else {
                int j = 0;
                for (String ext : extensions) {
                    if (ext == null)
                        throw new IllegalArgumentException("extensions[" + j + "] == null!");
                    j++;
                    configFile = new File(dir, pathToFile + "." + ext);
                    boolean fileExists = configFile.exists();
                    boolean reallyInsideDir = configFile.getAbsolutePath().startsWith(dir.getAbsolutePath());
                    if (!fileExists)
                        logger.trace("Configfile not found: " + configFile);
                    else if (!reallyInsideDir)
                        logger.debug("Configfile found but not inside dir: " + configFile);
                    if (fileExists && reallyInsideDir)
                        return configFile;
                }
            }
        }
        return null;
    }

    /**
     * Add a parenthesized number to a file name to get a file name that doesn't
     * exist yet.
     *
     * @param file the file that exists already
     * @return a file with a number added that doesn't exist yet
     */
    public static File addNumberToExistingFileName(File file) {
        File parentFile = file.getAbsoluteFile().getParentFile();
        String name = file.getName();
        int number = 2;
        File newFile;
        do {
            newFile = new File(parentFile, name + " (" + number + ")");
            number++;
        } while (newFile.exists());
        return newFile;
    }

    /**
     * Returns files in a directory, sorted.
     *
     * Sorts alphabetically, case-insensitively, and puts subdirectories first.
     *
     * @param dir the directory to list files in
     * @return the sorted array of files
     */
    public static File[] listFilesSorted(File dir) {
        File[] files = dir.listFiles();
        if (files == null)
            throw new BlackLabRuntimeException("Error listing in directory: " + dir);
        Arrays.sort(files, LIST_FILES_COMPARATOR);
        return files;
    }

    /**
     * Convert a simple file glob expression (containing * and/or ?) to a regular
     * expression.
     *
     * Example: "log*.txt" becomes "^log.*\\.txt$"
     *
     * @param glob the file glob expression
     * @return the regular expression
     */
    public static String globToRegex(String glob) {
        glob = glob.replaceAll("\\^", "\\\\\\^");
        glob = glob.replaceAll("\\$", "\\\\\\$");
        glob = glob.replaceAll("\\.", "\\\\.");
        glob = glob.replaceAll("\\\\", "\\\\");
        glob = glob.replaceAll("\\*", ".*");
        glob = glob.replaceAll("\\?", ".");
        return "^" + glob + "$";
    }
}
