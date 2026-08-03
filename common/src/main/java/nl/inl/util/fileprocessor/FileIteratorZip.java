package nl.inl.util.fileprocessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.util.ZipHandleManager;

/** Iterate over the files in a .zip archive. */
public class FileIteratorZip extends FileIteratorAbstract {

    private final FileReference archiveFile;

    /** Path to a single file in the zip file, or null to iterate over all files */
    private final String pathInZip;

    private ZipFile zipFile;

    private InputStream fileInputStream;

    private ZipInputStream zipInputStream;

    private ZipEntry lookahead;

    /** Did we just retrieve a single entry? */
    private boolean single = false;

    public FileIteratorZip(FileReference archiveFile, String pathInZip, FileIterator.FileIteratorSettings settings) {
        super(settings);
        this.archiveFile = archiveFile;
        this.pathInZip = pathInZip;
        if (pathInZip != null && archiveFile.getFile() != null) {
            // Get the entry directly from the file
            try {
                zipFile = ZipHandleManager.acquire(archiveFile.getFile());
                lookahead = zipFile.getEntry(pathInZip);
                if (lookahead == null)
                    throw new ErrorIndexingFile("File not found in archive: " + archiveFile.getFile() + "/" + pathInZip);
                single = true;
            } catch (IOException e) {
                throw new ErrorIndexingFile(e);
            }
        } else {
            // Find entries sequentially
            fileInputStream = archiveFile.getSinglePassInputStream();
            zipInputStream = new ZipInputStream(fileInputStream);
            lookAhead();
        }
    }

    @Override
    public FileIterator.FileIteratorSettings settings() {
        return settings;
    }

    @Override
    public void close() {
        try {
            if (zipInputStream != null)
                zipInputStream.close();
            if (fileInputStream != null)
                fileInputStream.close();
            if (zipFile != null)
                ZipHandleManager.release(zipFile);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void lookAhead() {
        if (single) {
            // We were only interested in a single file, so we're done
            lookahead = null;
            return;
        }
        try {
            boolean done = false;
            while (!done) {
                ZipEntry zipEntry = zipInputStream.getNextEntry();
                if (zipEntry == null) {
                    done = true;
                    lookahead = null;
                } else if (accept(zipEntry)) {
                    lookahead = zipEntry;
                    break;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error reading zip file: " + archiveFile, e);
        }
    }

    private boolean accept(ZipEntry zipEntry) {
        return !zipEntry.isDirectory() && (pathInZip == null || zipEntry.getName().equals(pathInZip));
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
        InputStream is = zipInputStream;
        try {
            if (single)
                is = zipFile.getInputStream(lookahead);
            try {
                // We have to convert the InputStream to a byte[], because we will
                // pass it to the handler asynchronously, and we can't guarantee that
                // the InputStream will still be valid when the handler is called.
                // (we can't use char[], even though that would be better for XML files,
                //  because file may be binary)
                String path = FilenameUtils.concat(archiveFile.getPath(), lookahead.getName());
                return FileReference.fromBytes(path, IOUtils.toByteArray(is),
                        archiveFile.getAssociatedFile());
            } catch (IOException e) {
                throw new IllegalStateException("Error reading zip entry", e);
            }  finally {
                if (single)
                    is.close();
            }
        } catch (IOException e) {
            throw new ErrorIndexingFile(e);
        }
    }
}
