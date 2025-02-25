package nl.inl.util;

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
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

public class FileReferenceFile implements FileReference {

    /** The file */
    private File file;

    /** The encoding, or null if BOM not yet detected */
    private Charset charSet;

    FileReferenceFile(File file) {
        this.file = file;
    }

    @Override
    public String getPath() {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    @Override
    public byte[] getBytes() {
        if (file.length() > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new IllegalArgumentException("Content doesn't fit in a byte array");
        try {
            return FileUtils.readFileToByteArray(file);
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
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
                throw new BlackLabRuntimeException(e);
            }
        }
        return this;
    }

    public InputStream getSinglePassInputStream() {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    public BufferedReader createReader(Charset overrideEncoding) {
        if (overrideEncoding == null)
            overrideEncoding = getCharSet();
        try {
            return new BufferedReader(new InputStreamReader(new FileInputStream(file), overrideEncoding));
        } catch (FileNotFoundException e) {
            throw new BlackLabRuntimeException(e);
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
                throw new BlackLabRuntimeException(e);
            }
        }
        return charSet;
    }
}
