package nl.inl.util.fileprocessor;

import java.util.Iterator;
import java.util.regex.Pattern;

/** Base class for file iterators. Manages settings. */
public abstract class FileIteratorAbstract implements FileIterator {

    protected final FileIteratorSettings settings;

    /**
     * Restrict the files we handle to a file glob? Note that this pattern is not
     * applied to directories, and directories within archives. It is also not
     * applied to the input file directly.
     */
    protected final Pattern pattGlob;

    protected Iterator<FileReference> iterator;

    public FileIteratorAbstract(FileIteratorSettings settings) {
        this.settings = settings;
        pattGlob = settings.pattGlob();
    }

    @Override
    public FileIteratorSettings settings() {
        return settings;
    }

    @Override
    public Pattern pattGlob() {
        return pattGlob;
    }
}
