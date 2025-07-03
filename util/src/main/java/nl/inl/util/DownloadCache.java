package nl.inl.util;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.io.FileUtils;

/**
 * Manages downloaded files.
 *
 * Downloading files takes time, so it's more efficient to keep them around for
 * a while in case we'll access the same file again. Of course, we should
 * eventually delete them to free up resources as well.
 */
public class DownloadCache {

    /**
     * How this class can be configured.
     */
    public interface Config {
        boolean isDownloadAllowed();

        String getDir();

        long getSize();

        long getMaxFileSize();
    }

    private static Config config;

    /** Maximum age of downloaded file in sec */
    private static final int maxDownloadAgeSec = 24 * 3600;

    /** Maximum size of all files downloaded combined */
    private static final long maxDownloadFolderSize = 100_000_000;

    /** Where to download files (or null to use the system temp dir) */
    private static File downloadTempDir;

    /** Files we've downloaded to a temp dir. Will be deleted on exit. */
    static final Map<String, Download> downloadedFiles = new HashMap<>();

    static long downloadFolderSize = 0;

    static class Download implements Comparable<Download> {

        public final String key;

        public final File file;

        public long lastUsed;

        public Download(String key, File file) {
            this.key = key;
            this.file = file;
            this.lastUsed = System.currentTimeMillis();
        }

        @Override
        public int compareTo(Download o) {
            return Long.compare(lastUsed, o.lastUsed);
        }

        public void markUsed() {
            lastUsed = System.currentTimeMillis();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Download download))
                return false;
            return lastUsed == download.lastUsed && Objects.equals(key, download.key) && Objects.equals(
                    file, download.file);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, file, lastUsed);
        }

        public long timeSinceLastUsed() {
            return System.currentTimeMillis() - lastUsed;
        }

        public void delete() {
            if (file.exists() && !file.delete())
                throw new RuntimeException("Unable to delete downloaded file: " + file);
        }

        public long size() {
            return file.length();
        }

    }

    public static void setConfig(Config config) {
        DownloadCache.config = config;
    }

    /**
     * Check the size of a file pointed to by a URL.
     *
     * @param url url to check
     * @return file size
     */
    private static int getUrlSize(URL url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("HEAD");
            c.getInputStream();
            return c.getContentLength();
        } catch (IOException e) {
            return -1;
        } finally {
            if (c != null)
                c.disconnect();
        }
    }

    private synchronized static void removeEntriesIfRequired() {

        // Remove any entries that haven't been used for a long time
        List<Download> dl = new ArrayList<>(downloadedFiles.values());
        Iterator<Download> it = dl.iterator();
        while (it.hasNext()) {
            Download download = it.next();
            if (download.timeSinceLastUsed() > maxDownloadAgeSec) {
                downloadedFiles.remove(download.key);
                downloadFolderSize -= download.size();
                download.delete();
                it.remove();
            }
        }

        // If the cache is too big, remove entries that haven't been used the longest
        if (downloadFolderSize > maxDownloadFolderSize) {
            dl.sort(Comparator.naturalOrder());
            it = downloadedFiles.values().iterator();
            while (downloadFolderSize > maxDownloadFolderSize && it.hasNext()) {
                Download download = it.next();
                downloadFolderSize -= download.size();
                download.delete(); // delete the file
                it.remove();
            }
        }
    }

    /**
     * Download file to temp file if it hasn't been downloaded already.
     *
     * @param inputFile URL of the file
     * @return temp file
     */
    public synchronized static File downloadFile(String inputFile) throws IOException, MalformedURLException {
        if (!isFileDownloadAllowed())
            throw new AccessDeniedException(inputFile, null, "Http downloads have been disabled");
        Download download = downloadedFiles.get(inputFile);
        if (download == null) {
            URL url = new URL(inputFile);
            int urlSize = getUrlSize(url);
            if (urlSize > getMaxDownloadedFileSize())
                throw new UnsupportedOperationException(
                        "File too large (" + urlSize + " > " + getMaxDownloadedFileSize() + ")");
            String ext = inputFile.replaceAll("^.+(\\.[^.]+)$", "$1");
            if (ext == null || ext.isEmpty())
                ext = ".xml";
            File tempFile = File.createTempFile("BlackLab_download_", ext, getDownloadTempDir());
            tempFile.deleteOnExit();
            FileUtils.copyURLToFile(url, tempFile);
            download = new Download(inputFile, tempFile);
            downloadedFiles.put(inputFile, download);
            downloadFolderSize += tempFile.length();
        }
        download.markUsed();
        removeEntriesIfRequired();
        return download.file;
    }

    public static boolean isFileDownloadAllowed() {
        return config.isDownloadAllowed();
    }

    private static long getMaxDownloadedFileSize() {
        return Math.min(maxDownloadFolderSize, config.getMaxFileSize());
    }

    public synchronized static File getDownloadTempDir() {
        if (downloadTempDir == null) {
            if (config.getDir() != null) {
                downloadTempDir = new File(config.getDir());
            } else {
                downloadTempDir = new File(System.getProperty("java.io.tmpdir"), "bls-download-cache");
            }
        }
        if (!downloadTempDir.exists()) {
            if (!downloadTempDir.mkdir())
                throw new RuntimeException("Could not create dir: " + downloadTempDir);
            downloadTempDir.deleteOnExit();
        }
        return downloadTempDir;
    }
}
