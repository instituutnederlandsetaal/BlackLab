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

    static FileIterator from(File file, FileIteratorSettings settings) {
        if (file.isDirectory()) { // Even if recurseSubdirs is false, we should process all direct children
            return new FileIteratorDir(file, settings);
        } else {
            return fileReferenceToFileIterator(FileReference.fromFile(file), settings);
        }
    }

    /** Settings for the file iteration process. */
    record FileIteratorSettings(boolean recurseSubdirs, boolean processArchives, String fileNameGlob) {

        public static final FileIteratorSettings DUMMY = new FileIteratorSettings(true, true, "*");

        public FileIteratorSettings {
            if (fileNameGlob == null || fileNameGlob.isEmpty())
                fileNameGlob = "*";
        }

        Pattern pattGlob() {
            return Pattern.compile(FileUtil.globToRegex(this.fileNameGlob));
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
     * Should we skip the specified file because it is a special OS file?
     *
     * Skips Windows Thumbs.db file and Mac OSX .DS_Store file.
     *
     * @param fileName name of the file
     * @return true if we should skip it, false otherwise
     */
    default boolean includeFile(String fileName) {
        //Skip files like Thumbs.db (Windows) and .DS_Store (OSX)
        return !fileName.equals("Thumbs.db") && !fileName.equals(".DS_Store") &&
                pattGlob().matcher(fileName).matches();
    }

    Pattern pattGlob();

    void close();

}
