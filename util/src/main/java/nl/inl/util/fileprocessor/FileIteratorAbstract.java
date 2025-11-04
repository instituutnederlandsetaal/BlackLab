package nl.inl.util.fileprocessor;

import java.util.Iterator;
import java.util.regex.Pattern;

/** Base class for file iterators. Manages settings. */
public abstract class FileIteratorAbstract implements FileIterator {

    protected final FileIteratorSettings settings;

    /**
     * Restrict the files we handle to a file glob? Note that this pattern is not
     * applied to directories.
     */
    protected final Pattern pattFileNameGlobGlobal;

    protected Iterator<FileReference> iterator;

    public FileIteratorAbstract(FileIteratorSettings settings) {
        this.settings = settings;
        pattFileNameGlobGlobal = settings.pattFileNameGlobGlobal();
    }

    @Override
    public FileIteratorSettings settings() {
        return settings;
    }

    @Override
    public boolean includeFile(String fileName) {
        //Skip files like Thumbs.db (Windows) and .DS_Store (OSX)
        return !fileName.equals("Thumbs.db") && !fileName.equals(".DS_Store") &&
                pattFileNameGlobGlobal.matcher(fileName).matches();
    }

}
