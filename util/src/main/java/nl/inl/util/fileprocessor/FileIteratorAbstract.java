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

    public FileIteratorAbstract(FileIteratorSettings settings) {
        this.settings = settings;
        pattFileNameGlobGlobal = settings.pattFileNameGlobGlobal();
    }

    @Override
    public FileIteratorSettings settings() {
        return settings;
    }

    @Override
    public void close() {
        // Nothing to do by default
    }
}
