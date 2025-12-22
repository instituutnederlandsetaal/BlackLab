package nl.inl.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.BOMInputStream;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;

public class FileReferenceFile implements FileReference {

    /** The file */
    private final File file;

    /** The encoding, or null if BOM not yet detected */
    private Charset charSet;

    /** Cached canonical path (can be quite slow to retrieve) */
    private String canonicalPath;

    FileReferenceFile(File file) {
        this.file = file;
    }

    @Override
    public String getPath() {
        if (canonicalPath == null) {
            try {
                canonicalPath = file.getCanonicalPath();
            } catch (IOException e) {
                throw new ErrorIndexingFile(e);
            }
        }
        return canonicalPath;
    }

    @Override
    public byte[] getBytes() {
        if (file.length() > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new IllegalArgumentException("Content doesn't fit in a byte array");
        try {
            return FileUtils.readFileToByteArray(file);
        } catch (IOException e) {
            throw new ErrorIndexingFile(e);
        }
    }

    @Override
    public FileReference withCreateReader() {
        return this;
    }

    @Override
    public FileReference inMemoryIfSmallerThan(int maxFileSizeBytes) {
        if (file.length() < maxFileSizeBytes) {
            try {
                return FileReference.readIntoMemoryFromTextualInputStream(getPath(), new FileInputStream(file), file);
            } catch (IOException e) {
                throw new ErrorIndexingFile(e);
            }
        }
        return this;
    }

    @Override
    public InputStream getSinglePassInputStream() {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new ErrorIndexingFile(e);
        }
    }

    @Override
    public BufferedReader createReader(Charset overrideEncoding) {
        if (overrideEncoding == null)
            overrideEncoding = getCharSet();
        try {
            // 10% of file size within range 8KB-8MB; speed up indexing from remote filesystems
            // buffer as close to the IO as possible, as InputStreamReader uses a miniscule internal buffer.
            // If we don't buffer its source InputStream, it will fire thousands of tiny reads
            // and severely slow down indexing from a remote filesystem.
            var bufferSize = (int) Math.max(8 * 1024, Math.min(file.length() * 0.1 + 1, 8 * 1024 * 1024));
            var baseFileInputStream = new BufferedInputStream(new FileInputStream(file), bufferSize);
            return new BufferedReader(new InputStreamReader(baseFileInputStream, overrideEncoding)); // use default buffer size here.
        } catch (FileNotFoundException e) {
            throw new ErrorIndexingFile(e);
        }
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public File getAssociatedFile() {
        return file;
    }

    @Override
    public Charset getCharSet() {
        if (charSet == null) {
            // Check the file for a BOM to determine the encoding
            try (BOMInputStream is = UnicodeStream.wrap(new FileInputStream(file))) {
                charSet = UnicodeStream.getCharset(is);
            } catch (IOException e) {
                throw new ErrorIndexingFile(e);
            }
        }
        return charSet;
    }
}
