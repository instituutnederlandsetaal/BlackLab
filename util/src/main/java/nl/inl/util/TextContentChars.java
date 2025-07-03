package nl.inl.util;

/**
 * Text content, either as bytes or as a String.
 */
public class TextContentChars implements TextContent {

    /** chars buffer for text content (use offset and length as well). */
    private final char[] chars;

    /** start offset of text content */
    private final int offset;

    /** length of text content (in chars) */
    private final int length;

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

    @Override
    public boolean isEmpty() {
        return length == 0;
    }

    /**
     * Append this text content to a string builder.
     * @param builder where to add our content
     */
    @Override
    public void appendToStringBuilder(StringBuilder builder) {
        builder.append(chars, offset, length);
    }

    @Override
    public String toString() {
        return new String(chars, offset, length);
    }
}
