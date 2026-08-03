package nl.inl.util.fileprocessor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.util.TextContent;
import nl.inl.util.UnicodeStream;

/** Represents a file to be indexed.
 * <p>
 * May be in the form of an input stream, byte array, or file.
 */
public interface FileReference {

    /** A dummy file reference. FileIterator may return this; it will simply be skipped.
     * Can be convenient when implementing an IndexSource that filters based on file content.
     */
    FileReference DUMMY = new FileReference() {
        @Override
        public String getPath() {
            return "<DUMMY>";
        }

        @Override
        public FileReference withCreateReader() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getSinglePassInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getAssociatedFile() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Charset getCharSet() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "DUMMY-FILE-REF";
        }
    };

    /** When we have a choice, should we prefer a byte array (true) or a char array (false)?
     * (byte arrays are more memory-efficient, char arrays are generally more CPU-efficient
     *  when processing the file multiple times)
     */
    boolean PREFER_BYTE_ARRAY = true;

    static FileReference fromBytes(String path, byte[] contents, File assocFile) {
        return new FileReferenceBytes(path, contents, assocFile);
    }

    static FileReference fromBytesOverrideCharset(String path, byte[] contents, File assocFile, Charset charset) {
        return new FileReferenceBytes(path, contents, assocFile, charset);
    }

    static FileReference fromFile(File file) {
        return new FileReferenceFile(file);
    }

    static FileReference fromInputStream(String path, InputStream is, File assocFile) {
        return new FileReferenceInputStream(path, is, assocFile);
    }

    /** Given a (temporary) textual input stream, read the file into memory and return a file reference. */
    static FileReference readIntoMemoryFromTextualInputStream(String path, InputStream is, File assocFile) {
        try {
            if (PREFER_BYTE_ARRAY) {
                // Read into byte array. This takes less memory, but character decoding has
                // to be done each time we process the file.
                return fromBytes(path, IOUtils.toByteArray(is), assocFile);
            } else {
                // Read into char array. This will generally take about twice as much memory
                // (for Latin text), but we only do the character decoding once if we process
                // the file multiple times (e.g. parse XML, get content to store)
                BOMInputStream bis = UnicodeStream.wrap(is);
                char[] chars = IOUtils.toCharArray(bis, UnicodeStream.getCharset(bis));
                return fromCharArray(path, chars, assocFile);
            }
        } catch (IOException e) {
            throw new ErrorIndexingFile(e);
        }
    }

    static FileReference fromCharArray(String path, char[] charArray, File assocFile) {
        return new FileReferenceChars(path, charArray, assocFile);
    }

    static FileReference optimal(boolean isMultiThreaded, String path, InputStream is, File assocFile) {
        if (isMultiThreaded) {
            // We have to convert the InputStream to a byte[], because we will
            // pass it to the handler asynchronously, and we can't guarantee that
            // the InputStream will still be valid when the handler is called.
            // (we can't use char[], even though that would be better for XML files,
            //  because file may be binary)
            try {
                return FileReference.fromBytes(path, IOUtils.toByteArray(is), assocFile);
            } catch (IOException e) {
                throw new IllegalStateException("Error reading file: " + path, e);
            }
        } else {
            // We're only processing files synchronously, on this thread.
            // No need to convert to a byte[], just use the InputStream directly.
            return FileReference.fromInputStream(path, is, assocFile);
        }
    }

    /**
     * Path to the file (containing archive may be included).
     */
    String getPath();

    /** Return a file reference where createReader() works,
     *  so we can process the file multiple times (e.g. parse XML, get document contents to store).
     * <p>
     *  The returned FileReference may be this one, or a new one. It will either have the file in memory
     *  as bytes or chars, or be a file on disk.
     *
     *  @return this or a new FileReference
     */
    FileReference withCreateReader();

    /**
     * If we know this is a small file, read in into memory.
     *
     * @param maxFileSizeBytes threshold for reading into memory
     * @return this or a new FileReference
     */
    default FileReference inMemoryIfSmallerThan(int maxFileSizeBytes) {
        return this;
    }

    /**
     * Get an input stream to the file contents.
     * Call this if you only need to process the file ONCE.
     * Supported by all implementations.
     *
     * @return input stream
     */
    InputStream getSinglePassInputStream();

    /**
     * Get a reader to the file contents.
     * Call this if you only need to process the file ONCE.
     * Supported by all implementations.
     *
     * @return reader
     */
    default BufferedReader getSinglePassReader() {
        return new BufferedReader(new InputStreamReader(getSinglePassInputStream()));
    }

    /**
     * Get a reader to the file contents.
     * May be called multiple times. Not supported by all implementations; call withCreateReader() to get
     * a version of this FileReference that does support it.
     * @return reader
     */
    default BufferedReader createReader() {
        return createReader(null);
    }

    /**
     * Get a reader to the file contents.
     * May be called multiple times. Not supported by all implementations; call withCreateReader() to get
     * a version of this FileReference that does support it.
     * @param overrideEncoding if not null, use this encoding instead of the detected/configured one
     * @return reader
     */
    default BufferedReader createReader(Charset overrideEncoding) {
        throw new UnsupportedOperationException("Cannot create reader; call withCreateReader() first");
    }

    /** Is getTextContent(start, end) supported?
     * <p>
     * Only supported for implementations that can do it efficiently (i.e. with random access).
     */
    default boolean hasGetTextContent() {
        return false;
    }

    /**
     * Get part of the document.
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the content read
     */
    default TextContent getTextContent(long startOffset, long endOffset) {
        // We could do this using a Reader, but better to leave managing that to the caller,
        // which knows if it needs multiple parts of the file and can make sure to minimize
        // passes over the file.
        throw new UnsupportedOperationException("Cannot get text content; call withCreateReader() on the FileReference first");
    }

    /**
     * This file, or null if this is not a (simple) file.
     */
    default File getFile() {
        return null;
    }

    /**
     * The corresponding file or archive this content is (originally) from, or null if unknown.
     */
    File getAssociatedFile();

    /** Detected or configured charset to use for file (or just the default) */
    Charset getCharSet();
}
