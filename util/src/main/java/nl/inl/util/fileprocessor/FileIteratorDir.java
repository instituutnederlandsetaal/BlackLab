package nl.inl.util.fileprocessor;

import java.io.File;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import nl.inl.util.ConcatenatedIterator;
import nl.inl.util.FileUtil;

/**
 * Iterate over files in a directory matching a glob.
 * <p>
 * Depending on settings, will also process subdirectories and/or archive files.
 * Settings can also restrict which files to include based on a glob (which applies
 * recursively (IndexTool --file-glob setting), unlike the glob passed to the constructor,
 * which is simply meant to expand to a list of files in this directory, similar to how
 * Unix tools work).
 * <p>
 * Will also descend into archives (zip, tar, tar.gz, tgz) if so configured.
 */
public class FileIteratorDir extends FileIteratorAbstract {

    protected Iterator<FileReference> iterator;

    public FileIteratorDir(File directory, String globFileInThisDir, FileIteratorSettings settings) {
        super(settings);
        /** Directory from where to start finding files, or a single file. */
        if (!directory.exists())
            throw new IllegalArgumentException("Directory does not exist: " + directory);
        if (!directory.isDirectory())
            throw new IllegalArgumentException("Not a directory: " + directory);
        List<FileReference> files = new ArrayList<>();
        listFiles(directory, Pattern.compile(FileUtil.globToRegex(globFileInThisDir)), files);
        iterator = new ConcatenatedIterator<>(files.iterator(), this::fileReferenceToFileIterator);
    }

    @Override
    public void close() {
        // Nothing to do
    }

    /**
     * Should we skip the specified file?
     * <p>
     * Skips Windows Thumbs.db file and Mac OSX .DS_Store file.
     * Also skips files not matching the global file name glob, if any.
     *
     * @param fileName name of the file
     * @return true if we should skip it, false otherwise
     */
    protected boolean includeFile(String fileName) {
        //Skip files like Thumbs.db (Windows) and .DS_Store (OSX)
        return !fileName.equals("Thumbs.db") && !fileName.equals(".DS_Store") &&
                pattFileNameGlobGlobal.matcher(fileName).matches();
    }

    private void listFiles(File fileOrDir, Pattern pattGlobFilesInThisDir, List<FileReference> result) {
        if (!fileOrDir.isDirectory()) {
            if (includeFile(fileOrDir.getName()))
                result.add(FileReference.fromFile(fileOrDir));
            return;
        }
        try {
            Files.walkFileTree(
                    fileOrDir.toPath(),
                    Collections.singleton(FileVisitOption.FOLLOW_LINKS),
                    settings.recurseSubdirs() ? Integer.MAX_VALUE : 1,
                    new SimpleFileVisitor<>() {
                        @Override
                        public java.nio.file.FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) {
                            String fileName = filePath.getFileName().toString();
                            if (attrs.isDirectory() && !settings.recurseSubdirs())
                                return FileVisitResult.SKIP_SUBTREE;
                            if (attrs.isOther())
                                return FileVisitResult.CONTINUE; // skip special files

                            // normal file-ish
                            if (includeFile(fileName) && (pattGlobFilesInThisDir == null
                                    || pattGlobFilesInThisDir.matcher(fileName).matches()))
                                result.add(FileReference.fromFile(filePath.toFile()));
                            return FileVisitResult.CONTINUE;
                        }
                    }
            );
        } catch (Exception e) {
            throw new RuntimeException("Error listing files in directory: " + fileOrDir, e);
        }
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public FileReference next() {
        return iterator.next();
    }
}
