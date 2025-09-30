package nl.inl.util.fileprocessor;

import java.util.NoSuchElementException;

/**
 * A single regular (non-archive) file to process.
 */
public class FileIteratorSingle extends FileIteratorAbstract {

    private final FileReference file;

    private boolean nexted = false;

    public FileIteratorSingle(FileReference file) {
        super(FileIteratorSettings.DUMMY); // settings not needed
        this.file = file;
    }

    @Override
    public void close() {
        // Nothing to do
    }

    @Override
    public boolean hasNext() {
        return !nexted;
    }

    @Override
    public FileReference next() {
        if (!hasNext())
            throw new NoSuchElementException("No more files");
        nexted = true;
        return file;
    }
}
