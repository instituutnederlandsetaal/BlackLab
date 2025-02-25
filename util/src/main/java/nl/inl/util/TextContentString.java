package nl.inl.util;

/**
 * Text content, either as bytes or as a String.
 */
public class TextContentString implements TextContent {

    /** text content as string. */
    private String str;

    TextContentString(String str) {
        if (str == null)
            throw new IllegalArgumentException("str == null");
        this.str = str;
    }

    public boolean isEmpty() {
        return str.isEmpty();
    }

    /**
     * Append this text content to a string builder.
     * @param builder where to add our content
     */
    public void appendToStringBuilder(StringBuilder builder) {
        builder.append(str);
    }

    @Override
    public String toString() {
        return str;
    }
}
