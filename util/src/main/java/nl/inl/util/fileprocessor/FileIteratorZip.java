package nl.inl.util.fileprocessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

/** Iterate over the files in a .zip archive. */
class FileIteratorZip extends FileIteratorAbstract {

    private final FileReference archiveFile;

    private final InputStream fileInputStream;

    private final ZipInputStream zipInputStream;

    private ZipEntry lookahead;

    private boolean done = false;

    public FileIteratorZip(FileReference archiveFile, FileIterator.FileIteratorSettings settings) {
        super(settings);
        this.archiveFile = archiveFile;
        fileInputStream = archiveFile.getSinglePassInputStream();
        zipInputStream = new ZipInputStream(fileInputStream);
        lookAhead();
    }

    @Override
    public FileIterator.FileIteratorSettings settings() {
        return settings;
    }

    @Override
    public void close() {
        try {
            zipInputStream.close();
            fileInputStream.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void lookAhead() {
        try {
            while (!done) {
                ZipEntry zipEntry = zipInputStream.getNextEntry();
                if (zipEntry == null) {
                    done = true;
                    lookahead = null;
                } else if (!zipEntry.isDirectory()) {
                    lookahead = zipEntry;
                    break;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error reading zip file: " + archiveFile, e);
        }
    }

    @Override
    public boolean hasNext() {
        return lookahead != null;
    }

    @Override
    public FileReference next() {
        if (lookahead == null)
            throw new NoSuchElementException("No more files");
        FileReference result = getFileReferenceForEntry();
        lookAhead();
        return result;
    }

    private FileReference getFileReferenceForEntry() {
        // We have to convert the InputStream to a byte[], because we will
        // pass it to the handler asynchronously, and we can't guarantee that
        // the InputStream will still be valid when the handler is called.
        // (we can't use char[], even though that would be better for XML files,
        //  because file may be binary)
        try {
            String path = FilenameUtils.concat(archiveFile.getPath(), lookahead.getName());
            return FileReference.fromBytes(path, IOUtils.toByteArray(zipInputStream),
                    archiveFile.getAssociatedFile());
        } catch (IOException e) {
            throw new IllegalStateException("Error reading zip entry", e);
        }
    }
}
