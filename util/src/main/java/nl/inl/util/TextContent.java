package nl.inl.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

public interface TextContent {

    static TextContent from(String content) {
        return new TextContentString(content);
    }

    static TextContent from(char[] content) {
        return new TextContentChars(content);
    }

    static TextContent from(char[] contents, int start, int length) {
        return new TextContentChars(contents, start, length);
    }

    static TextContent from(byte[] contents, int start, int length, Charset charset) {
        return new TextContentBytes(contents, start, length, charset);
    }

    static TextContent from(ByteArrayOutputStream cmdiBuffer, Charset charset) {
        return new TextContentBytes(cmdiBuffer, charset);
    }

    boolean isEmpty();

    void appendToStringBuilder(StringBuilder builder);

    @Override
    String toString();
}
