package nl.inl.util.fileprocessor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;

import nl.inl.util.FileUtil;

/**
 * Iterate over files to index.
 *
 * Depending on settings, this may include recursing into subdirectories and
 * processing archive files (zip, tar.gz, gz).
 *
 * Implementations are not thread-safe.
 */
public interface FileIterator extends Iterator<FileReference> {

    static FileIterator from(File dir, String glob, FileIteratorSettings settings) {
        if (!dir.exists())
            throw new IllegalArgumentException("File or directory does not exist: " + dir);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + dir);
        }
        return new FileIteratorDir(dir, glob, settings);
    }

    static FileIterator from(File file, FileIteratorSettings settings) {
        if (file.isDirectory())
            return from(file, "*", settings);
        else
            return fileReferenceToFileIterator(FileReference.fromFile(file), settings);
    }

    static FileIterator from(FileReference file, FileIteratorSettings settings) {
        return fileReferenceToFileIterator(file, settings);
    }

    /** Settings for the file iteration process. */
    record FileIteratorSettings(boolean recurseSubdirs, boolean processArchives, String fileNameGlobGlobal) {

        public static final FileIteratorSettings DUMMY = new FileIteratorSettings(true, true, "*");

        public FileIteratorSettings {
            if (fileNameGlobGlobal == null || fileNameGlobGlobal.isEmpty())
                fileNameGlobGlobal = "*";
        }

        Pattern pattFileNameGlobGlobal() {
            return Pattern.compile(FileUtil.globToRegex(this.fileNameGlobGlobal));
        }

    }

    FileIteratorSettings settings();

    default Iterator<FileReference> fileReferenceToFileIterator(FileReference file) {
        return fileReferenceToFileIterator(file, settings());
    }

    static FileIterator fileReferenceToFileIterator(FileReference file, FileIteratorSettings settings) {
        if (settings.processArchives()) {
            // See if it's an archive we can process
            if (file.getPath().endsWith(".zip"))
                return new FileIteratorRecursive(new FileIteratorZip(file, settings));
            else if (file.getPath().endsWith(".tgz") || file.getPath().endsWith(".tar.gz"))
                return new FileIteratorRecursive(new FileIteratorTarGzip(file, settings));
            else if (file.getPath().endsWith(".gz")) {
                // Single GZipped file (last because we can process .tar.gz more efficiently)
                try (InputStream is = file.getSinglePassInputStream();
                        InputStream unzipped = new GzipCompressorInputStream(is)) {
                    String newPath = file.getPath().replaceAll("\\.gz$", "");
                    FileReference fileInZip = FileReference.fromBytes(
                            newPath, IOUtils.toByteArray(unzipped), file.getAssociatedFile());
                    return fileReferenceToFileIterator(fileInZip, settings);
                } catch (IOException e) {
                    throw new IllegalStateException("Error decompressing .gz file", e);
                }
            }
        }
        return new FileIteratorSingle(file);
    }

    /**
     * Should we skip the specified file?
     *
     * Skips Windows Thumbs.db file and Mac OSX .DS_Store file.
     * Also skips files not matching the global file name glob, if any.
     *
     * @param fileName name of the file
     * @return true if we should skip it, false otherwise
     */
    boolean includeFile(String fileName);

    void close();

}
