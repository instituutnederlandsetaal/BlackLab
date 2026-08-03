package nl.inl.util.fileprocessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

/** Iterate over the files in a .tar.gz archive. */
public class FileIteratorTarGzip extends FileIteratorAbstract {

    private final FileReference archiveFile;

    /** Path to a single file in the zip file, or null to iterate over all files */
    private final String pathInZip;

    private final InputStream fileInputStream;

    private final InputStream zipInputStream;

    private final TarArchiveInputStream tarArchiveInputStream;

    private TarArchiveEntry lookahead;

    private boolean done = false;

    public FileIteratorTarGzip(FileReference archiveFile, String pathInZip, FileIterator.FileIteratorSettings settings) {
        super(settings);
        this.archiveFile = archiveFile;
        this.pathInZip = pathInZip;
        fileInputStream = archiveFile.getSinglePassInputStream();
        try {
            zipInputStream = new GzipCompressorInputStream(fileInputStream);
            tarArchiveInputStream = new TarArchiveInputStream(zipInputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Error opening .tar.gz file", e);
        }
        lookAhead();
    }

    @Override
    public void close() {
        try {
            tarArchiveInputStream.close();
            zipInputStream.close();
            fileInputStream.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void lookAhead() {
        try {
            while (!done) {
                TarArchiveEntry zipEntry = tarArchiveInputStream.getNextEntry();
                if (zipEntry == null)
                    done = true;
                else if (accept(zipEntry)) {
                    lookahead = zipEntry;
                    break;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error reading tar archive: " + archiveFile, e);
        }
    }

    private boolean accept(TarArchiveEntry zipEntry) {
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
