package nl.inl.util.fileprocessor;

import java.util.Iterator;

import nl.inl.util.ConcatenatedIterator;

/** Iterate over a list of files recursively, that is, descending into archives if applicable. */
public class FileIteratorRecursive extends FileIteratorAbstract {

    private final FileIterator sourceIterator;

    private final Iterator<FileReference> iterator;

    public FileIteratorRecursive(FileIterator source) {
        super(source.settings());
        sourceIterator = source;

        // Create an iterator that goes through all entries in the zip file,
        // and deals with e.g. embedded archives.
        this.iterator = new ConcatenatedIterator<>(sourceIterator, this::fileReferenceToFileIterator);
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public FileReference next() {
        return iterator.next();
    }

    @Override
    public void close() {
        sourceIterator.close();
    }

}
