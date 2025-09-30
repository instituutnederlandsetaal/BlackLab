package nl.inl.util.fileprocessor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import nl.inl.util.ConcatenatedIterator;
import nl.inl.util.FileUtil;

/**
 * Iterate over files in a directory (and optionally its subdirectories).
 *
 * Will also descend into archives (zip, tar, tar.gz, tgz) if so configured.
 */
public class FileIteratorDir extends FileIteratorAbstract {

    public FileIteratorDir(File fileOrDir, FileIteratorSettings settings) {
        super(settings);
        /** Directory from where to start finding files, or a single file. */
        if (!fileOrDir.exists())
            throw new IllegalArgumentException("Directory does not exist: " + fileOrDir);
        if (!fileOrDir.isDirectory())
            throw new IllegalArgumentException("Not a directory: " + fileOrDir);
        List<FileReference> files = new ArrayList<>();
        listFiles(fileOrDir, files);
        iterator = new ConcatenatedIterator<>(files.iterator(), this::fileReferenceToFileIterator);
    }

    @Override
    public void close() {
        // Nothing to do
    }

    private void listFiles(File fileOrDir, List<FileReference> result) {
        if (fileOrDir.isDirectory()) { // Even if recurseSubdirs is false, we should process all direct children
            // Process files in directory, and recurse into subdirectories if needed
            for (File childFile: FileUtil.listFilesSorted(fileOrDir)) {
                if (settings.recurseSubdirs() || !childFile.isDirectory())
                    listFiles(childFile, result);
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
