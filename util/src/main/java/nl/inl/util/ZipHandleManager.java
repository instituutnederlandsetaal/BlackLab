package nl.inl.util;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;

/**
 * Manages opened zip files.
 *
 * Openings large zip files takes time, so it's more efficient to keep zip files
 * open for a while in case we'll access the same zip file again. Of course, we
 * should eventually close them to free up resources as well.
 */
public class ZipHandleManager {

    public static final int MAX_OPEN_AGE_SEC = 3600;

    public static final int MAX_OPEN_HANDLES = 10;

    private static ObjectCache<ZipFile, File> handleManager;

    static {
        handleManager = new ObjectCache<>(
                (File f) -> {
                    try {
                        return new ZipFile(f);
                    } catch (IOException e) {
                        throw new ErrorIndexingFile(e);
                    }
                },
                (ZipFile zf) -> {
                    try {
                        zf.close();
                    } catch (IOException e) {
                        throw new ErrorIndexingFile(e);
                    }
                },
                MAX_OPEN_HANDLES, MAX_OPEN_AGE_SEC * 1000
        );
    }

    public static void closeAllZips() {
        handleManager.closeAll();
    }

    public static ZipFile acquire(File zipFile) throws IOException {
        return handleManager.acquire(zipFile);
    }

    public static void release(ZipFile zipFile) {
        handleManager.releaseObject(zipFile);
    }

    public static void setMaxOpen(int zipFilesMaxOpen) {
        handleManager.setMaxCacheSize(zipFilesMaxOpen);
    }

    public static void setMaxOpenAgeSec(int zipFilesMaxAgeSec) {
        handleManager.setMaxAgeSec(zipFilesMaxAgeSec);
    }
}
