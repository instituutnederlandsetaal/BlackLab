package nl.inl.blacklab.indexers.config.saxon;

import java.io.File;
import java.io.Reader;

import nl.inl.util.TextContent;
import nl.inl.util.fileprocessor.FileReference;

public interface XmlDocRef {

    static XmlDocRef fromFileReference(FileReference file) {
        return new XmlDocRefFileReference(file);
    }

    void setXIncludeDirectory(File dir);

    /**
     * Get the document as a Reader. May be called multiple times.
     * @return the reader
     */
    Reader getDocumentReader();

    /**
     * Get part of the document as a TextContent object.
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the content read
     */
    TextContent getTextContent(long startOffset, long endOffset);

    /**
     * Clean up resources.
     */
    void clean();
}
