package nl.inl.blacklab.index;

import java.io.File;
import java.util.Optional;

import nl.inl.util.fileprocessor.FileIterator;

/** A path that represents a collection of files from some source
 * (file system, database, ...) */
public abstract class IndexSource {

    private final String path;

    private FileIterator.FileIteratorSettings fileIteratorSettings = new FileIterator.FileIteratorSettings(true, true, "*");

    public void setFileIteratorSettings(FileIterator.FileIteratorSettings fileIteratorSettings) {
        this.fileIteratorSettings = fileIteratorSettings;
    }

    public FileIterator.FileIteratorSettings getFileIteratorSettings() {
        return fileIteratorSettings;
    }

    public IndexSource(String path) {
        this.path = path;
    }

    /**
     * Get directory associated with this IndexSource; we will search it for format files.
     */
    public Optional<File> getAssociatedDirectory() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return path;
    }

    public abstract FileIterator filesToIndex();
}
