package nl.inl.blacklab.indexers.config.saxon;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.function.Supplier;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.Constants;
import nl.inl.util.CountingReader;
import nl.inl.util.TextContent;

/** A way to access the contents of an XML document.
 *
 * Contents may be stored in memory for smaller documents, or be read from disk for larger ones.
 */
public abstract class XmlDocRefAbstract implements XmlDocRef {

    /** Helper for resolving XIncludes */
    Supplier<CountingReader> xincludeResolver;

    /** Are we resolving xi:includes? If not, we can use the base contents directly. */
    protected boolean resolvingXIncludes = false;

    /** Internal Reader for getTextContent(). */
    private CountingReader internalReader = null;

    /**
     * Read characters from a Reader, starting at startOffset and ending at endOffset.
     *
     * @param reader the Reader to read from
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the characters read
     * @throws IOException
     */
    private static char[] readerToCharArray(Reader reader, long startOffset, long endOffset) {
        if (endOffset - startOffset > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new IllegalArgumentException("Content doesn't fit char array");
        try {
            if (startOffset > 0)
                IOUtils.skip(reader, startOffset);
            if (endOffset != -1) {
                int length = (int)(endOffset - startOffset);
                char[] result = new char[length];
                if (reader.read(result, 0, length) < 0)
                    throw new RuntimeException("Unexpected end of file");
                return result;
            } else {
                return IOUtils.toCharArray(reader);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get part of the document as a TextContent object.
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the content read
     */
    @Override
    public TextContent getTextContent(long startOffset, long endOffset) {
        char[] result = readerToCharArray(getInternalReader(startOffset), 0, endOffset - startOffset);
        return TextContent.from(result);
    }

    /** Get a Reader at the specified position. */
    private Reader getInternalReader(long startOffset) {
        if (internalReader == null || internalReader.getCharsRead() > startOffset) {
            // No Reader yet, or too far along; create a new one.
            internalReader = getDocumentReader();
        }
        try {
            internalReader.skipTo(startOffset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (internalReader.getCharsRead() != startOffset) {
            throw new RuntimeException("Could not skip to start offset");
        }
        return internalReader;
    }

    @Override
    public void clean() {
        xincludeResolver = null;
        if (internalReader != null) {
            try {
                internalReader.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            internalReader = null;
        }
    }

    /**
     * Set directory for resolving XIncludes in the document.
     *
     * Activates the resolving for xi:include elements, which is disabled by default.
     *
     * @param dir the directory to resolve relative XInclude paths against
     * @return document reference with XIncludes resolved (may be the same or a new instance)
     */
    @Override
    public void setXIncludeDirectory(File dir) {
        xincludeResolver = new XIncludeResolver(getBaseDocReaderSupplier(), dir);
        resolvingXIncludes = true;
    }

    public abstract Supplier<CountingReader> getBaseDocReaderSupplier();

    @Override
    public CountingReader getDocumentReader() {
        if (xincludeResolver == null) {
            // No resolver configured; just return a reader for the base document
            xincludeResolver = getBaseDocReaderSupplier();
        }
        return xincludeResolver.get();
    }
}
