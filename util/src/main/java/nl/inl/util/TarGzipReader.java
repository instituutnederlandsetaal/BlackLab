package nl.inl.util;

import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FilenameUtils;

import nl.inl.blacklab.exceptions.BlackLabException;

/**
 * Class for handling .tar.gz input streams. It will call the supplied handler
 * for each "normal" file in the .tar.gz file.
 */
public class TarGzipReader {
    private TarGzipReader() {
    }

    /**
     * Handles a file inside the .tar.gz archive.
     */
    @FunctionalInterface
    public interface FileHandler {
      /**
        * @param filePath path to the archive, concatenated with any path to this file
        *            inside the archive
        * @param tmpInputStream stream to the file contents, to be consumed immediately
        * @return true if we should continue with the next file, false if not
        */
        boolean handle(String filePath, InputStream tmpInputStream);
    }

    /**
     * Process a .tar.gz file and call the handler for each normal file in the
     * archive.
     *
     * @param fileRef file to decompress
     * @param fileHandler the handler to call for each regular file
     */
    public static void processTarGzip(FileReference fileRef, FileHandler fileHandler) {
        try (InputStream tgzStream = fileRef.getSinglePassInputStream();
                InputStream unzipped = new GzipCompressorInputStream(tgzStream)) {
            processTar(fileRef.getPath(), unzipped, fileHandler);
        } catch (Exception e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Process a .tar.gz file and call the handler for each normal file in the
     * archive.
     *
     * @param fileName name/path to this file
     * @param tarGzipStream the .tar.gz input stream to decompress. The stream will
     *            be closed after processing.
     * @param fileHandler the handler to call for each regular file
     */
    public static void processTarGzip(String fileName, InputStream tarGzipStream, FileHandler fileHandler) {
        try (InputStream unzipped = new GzipCompressorInputStream(tarGzipStream)) {
            processTar(fileName, unzipped, fileHandler);
        } catch (Exception e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Process a .gz file and call the file handler for its contents.
     *
     * @param fileRef file to decompress
     * @param fileHandler the handler to call for each regular file
     */
    public static void processGzip(FileReference fileRef, FileHandler fileHandler) {
        try (InputStream gzStream = fileRef.getSinglePassInputStream();
                InputStream unzipped = new GzipCompressorInputStream(gzStream)) {
            fileHandler.handle(fileRef.getPath().replaceAll("\\.gz$", ""), unzipped);
            // TODO make filename handling uniform across all archives types?
        } catch (Exception e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Process a .gz file and call the file handler for its contents.
     *
     * @param fileName name/path to this file
     * @param gzipStream the .gz input stream to decompress. The stream will be
     *            closed after processing.
     * @param fileHandler the handler to call
     */
    public static void processGzip(String fileName, InputStream gzipStream, FileHandler fileHandler) {
        try (InputStream unzipped = new GzipCompressorInputStream(gzipStream)) {
            fileHandler.handle(fileName.replaceAll("\\.gz$", ""), unzipped);
            // TODO make filename handling uniform across all archives types?
        } catch (Exception e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Process a .tar file and call the handler for each normal file in the archive.
     *
     * @param fileName name/path to this file
     * @param tarStream the .tar input stream to decompress. The stream will be
     *            closed after processing.
     * @param fileHandler the handler to call for each regular file
     */
    public static void processTar(String fileName, InputStream tarStream, FileHandler fileHandler) {
        try (TarArchiveInputStream s = new TarArchiveInputStream(tarStream)) {
            for (TarArchiveEntry e = s.getNextTarEntry(); e != null; e = s.getNextTarEntry()) {
                if (e.isDirectory())
                    continue;

                boolean keepProcessing = fileHandler.handle(
                        FilenameUtils.concat(fileName, e.getName()), s);
                if (!keepProcessing)
                    return;
            }
        } catch (Exception e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Process a .zip file and call the handler for each normal file in the archive.
     *
     * Note that directory structure inside the zip file is ignored; files are
     * processed as if they are one large directory.
     *
     * @param fileRef file to decompress
     * @param fileHandler the handler to call for each regular file
     */
    public static void processZip(FileReference fileRef, FileHandler fileHandler) {
        try (InputStream zipStream = fileRef.getSinglePassInputStream();
                ZipInputStream s = new ZipInputStream(zipStream)) {
            for (ZipEntry e = s.getNextEntry(); e != null; e = s.getNextEntry()) {
                if (e.isDirectory())
                    continue;

                boolean keepProcessing = fileHandler.handle(FilenameUtils.concat(fileRef.getPath(),
                        e.getName()), s);
                if (!keepProcessing)
                    return;
            }
        } catch (Exception e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Process a .zip file and call the handler for each normal file in the archive.
     *
     * Note that directory structure inside the zip file is ignored; files are
     * processed as if they are one large directory.
     *
     * @param fileName name/path to this file
     * @param zipStream the .zip input stream to decompress. The stream will be
     *            closed after processing.
     * @param fileHandler the handler to call for each regular file
     */
    public static void processZip(String fileName, InputStream zipStream, FileHandler fileHandler) {
        try (ZipInputStream s = new ZipInputStream(zipStream)) {
            for (ZipEntry e = s.getNextEntry(); e != null; e = s.getNextEntry()) {
                if (e.isDirectory())
                    continue;

                boolean keepProcessing = fileHandler.handle(FilenameUtils.concat(fileName,
                        e.getName()), s);
                if (!keepProcessing)
                    return;
            }
        } catch (Exception e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }
}
