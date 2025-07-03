package nl.inl.blacklab.indexers.config.saxon;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.inl.util.CountingReader;

/** Resolve xi:includes using a composite Reader. */
class XIncludeResolver implements Supplier<CountingReader> {

    /** How we detect xi:include tags */
    private static final Pattern PATT_XINCLUDE_TAG = Pattern.compile("<xi:include\\s+href=\"([^\"]+)\"\\s*/>");

    private final File baseDir;

    private final Supplier<CountingReader> supplier;

    public XIncludeResolver(Supplier<CountingReader> supplier, File curDir) {
        this.supplier = supplier;
        this.baseDir = curDir;
    }

    @Override
    public CountingReader get() {

        return new CountingReader(new Reader() {

            /** Buffer length. Chosen large enough that it should be able to contain any tags encountered. */
            private static final int BUFFER_LENGTH = 1024;

            /** Reader for the base document */
            private final Reader baseDocReader = supplier.get();

            /** If not null, we've encountered an xi:include and should read from this
             *  as soon as our current buffer is empty. */
            private Reader includedDocReader;

            /** Buffered chars from base doc. Buffer wraps around. */
            private final char[] buffer = new char[BUFFER_LENGTH];

            /** Index of first buffered char */
            private int start = 0;

            /** Index after last buffered char */
            private int end = 0;

            private boolean done = false;

            private final List<File> includeFilesRead = new ArrayList<>();

            private boolean isBufferEmpty() {
                return start == end;
            }

            private boolean isBufferFull() {
                return (end + 1) % buffer.length == start;
            }

            /** Add one character to our buffer */
            private void addToBuffer(char c) throws IOException {
                buffer[end] = c;
                end++;
                if (end == buffer.length)
                    end = 0;
            }

            /** Delete n characters from the end of the buffer */
            private void deleteFromBuffer(int n) {
                end -= n;
                if (end < 0)
                    end += buffer.length;
            }

            /** Ensure there's at least one character available in our buffer */
            private boolean ensureBuffered() throws IOException {
                if (!isBufferEmpty())
                    return true;
                int c = baseDocReader.read();
                if (c < 0)
                    return false;
                addToBuffer((char) c);
                if (c == '<') {
                    // Make sure we buffer the entire tag
                    int tagStart = end == 0 ? buffer.length - 1 : end - 1;
                    while (!isBufferFull()) {
                        c = baseDocReader.read();
                        if (c < 0)
                            break;
                        addToBuffer((char) c);
                        if (c == '>')
                            break;
                    }
                    checkXInclude(tagStart);
                }
                return true;
            }

            public void checkXInclude(int tagStart) {
                String s;
                if (end >= tagStart) {
                    s = new String(buffer, tagStart, end - tagStart);
                } else {
                    s = new String(buffer, tagStart, buffer.length - tagStart) +
                        new String(buffer, 0, end);
                }
                Matcher m = PATT_XINCLUDE_TAG.matcher(s);
                if (m.matches()) {
                    String href = m.group(1);
                    try {
                        File f = new File(baseDir, href);
                        // Make sure we're not trying to break out of the base directory
                        if (!f.getCanonicalPath().startsWith(baseDir.getParentFile().getCanonicalPath())) {
                            throw new RuntimeException("XInclude file " + f + " is not within the directory " + baseDir.getParentFile());
                        }
                        includedDocReader = new FileReader(f, StandardCharsets.UTF_8);
                        includeFilesRead.add(f);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    // Remove the XInclude tag from the buffer
                    deleteFromBuffer(end - tagStart + (end < tagStart ? buffer.length : 0));
                }
            }

            @Override
            public int read() throws IOException {
                if (isBufferEmpty() && includedDocReader == null) {
                    // Empty buffer and no xi:include to read from
                    if (!ensureBuffered()) {
                        // We're done.
                        done = true;
                        return -1;
                    }
                }
                if (isBufferEmpty() && includedDocReader != null) {
                    // Empty buffer and we've encountered an xi:include tag;
                    // read from the included file
                    int c = includedDocReader.read();
                    if (c >= 0) {
                        return c;
                    }
                    // Done with the included file; resume with the main file
                    includedDocReader.close();
                    includedDocReader = null;
                    if (!ensureBuffered()) {
                        // We're done.
                        done = true;
                        return -1;
                    }
                }
                // Return first char from buffer
                char c = buffer[start];
                start++;
                if (start == buffer.length)
                    start = 0;
                return c;
            }

            @Override
            public int read(char[] chars, int offset, int length) throws IOException {
                int charsRead = 0;
                while (charsRead < length) {
                    int c = read();
                    if (c < 0)
                        return charsRead == 0 ? c : charsRead;
                    chars[offset] = (char)c;
                    offset++;
                    charsRead++;
                }
                return charsRead;
            }

            @Override
            public void close() throws IOException {
                if (includedDocReader != null)
                    includedDocReader.close();
                baseDocReader.close();
            }
        });
    }
}
