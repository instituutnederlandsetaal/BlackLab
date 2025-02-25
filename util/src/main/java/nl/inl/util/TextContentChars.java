package nl.inl.util;

import java.io.CharArrayReader;
import java.io.Reader;

/**
 * Text content, either as bytes or as a String.
 */
public class TextContentChars implements TextContent {

    /** chars buffer for text content (use offset and length as well). */
    private char[] chars;

    /** start offset of text content */
    private int offset;

    /** length of text content (in chars) */
    private int length;

    TextContentChars(char[] chars) {
        if (chars == null)
            throw new IllegalArgumentException("chars == null");
        this.chars = chars;
        this.offset = 0;
        this.length = chars.length;
    }

    TextContentChars(char[] chars, int offset, int length) {
        if (chars == null)
            throw new IllegalArgumentException("chars == null");
        if (offset < 0 || length < 0 || offset + length > chars.length)
            throw new IllegalArgumentException(
                    "illegal values for offset and length: " + offset + ", " + length + " (bytes.length = "
                            + chars.length + ")");
        this.chars = chars;
        this.offset = offset;
        this.length = length;
    }

    public boolean isEmpty() {
        return length == 0;
    }

    /**
     * Append this text content to a string builder.
     * @param builder where to add our content
     */
    public void appendToStringBuilder(StringBuilder builder) {
        builder.append(chars, offset, length);
    }

    @Override
    public String toString() {
        return new String(chars, offset, length);
    }
}
