package nl.inl.util;

import java.io.IOException;
import java.io.Reader;

/**
 * A Reader decorator that keeps track of the number of characters read.
 */
public class CountingReader extends Reader {
    /**
     * The Reader we're decorating
     */
    private final Reader reader_;

    /**
     * Character count
     */
    private long charsRead;

    /**
     * Last count reported
     */
    private long lastReported;

    /**
     * Constructor
     *
     * @param reader the Reader we're decorating
     */
    public CountingReader(Reader reader) {
        reader_ = reader;
        charsRead = lastReported = 0;
    }

    @Override
    public void close() throws IOException {
        reader_.close();
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int bytesReadThisTime = reader_.read(cbuf, off, len);
        if (bytesReadThisTime > 0)
            charsRead += bytesReadThisTime;
        return bytesReadThisTime;
    }

    public long getCharsRead() {
        lastReported = charsRead;
        return charsRead;
    }

    public long getCharsReadSinceLastCall() {
        long n = charsRead - lastReported;
        lastReported = charsRead;
        return n;
    }

    public long skipTo(long startOffset) throws IOException {
        return skip(startOffset - getCharsRead());
    }
}
