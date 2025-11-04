package nl.inl.util.fileprocessor;

import java.io.File;
import java.util.ArrayList;
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

    private void listFiles(File fileOrDir, Pattern pattGlobFilesInThisDir, List<FileReference> result) {
        if (fileOrDir.isDirectory()) { // Even if recurseSubdirs is false, we should process all direct children
            // Process files in directory, and recurse into subdirectories if needed
            for (File childFile: FileUtil.listFilesSorted(fileOrDir)) {
                if (pattGlobFilesInThisDir != null && !pattGlobFilesInThisDir.matcher(childFile.getName()).matches())
                    continue; // Skip non-matching files/dirs
                if (childFile.isFile() && !includeFile(childFile.getName()))
                    continue; // Skip non-included files
                if (settings.recurseSubdirs() || !childFile.isDirectory()) {
                    // Note that we don't pass the glob pattern when recursing into subdirs;
                    // only the global file name glob from the settings applies there (if specified).
                    listFiles(childFile, null, result);
                }
            }
        } else if (includeFile(fileOrDir.getName())) {
            // Add the file
            result.add(FileReference.fromFile(fileOrDir));
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
