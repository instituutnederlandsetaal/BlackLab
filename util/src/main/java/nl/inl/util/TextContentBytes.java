package nl.inl.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * Text content, either as bytes or as a String.
 */
public class TextContentBytes implements TextContent {

    /** If not null: bytes buffer for text content (use offset and length as well). str and chars will be null. */
    private byte[] bytes;

    /** start offset of text content */
    private int offset;

    /** length of text content (in bytes) */
    private int length;

    /** charset to use */
    private Charset bytesCharset;

    TextContentBytes(byte[] bytes, int offset, int length, Charset charset) {
        if (bytes == null)
            throw new IllegalArgumentException("bytes == null");
        if (offset < 0 || length < 0 || offset + length > bytes.length)
            throw new IllegalArgumentException(
                    "illegal values for offset and length: " + offset + ", " + length + " (bytes.length = "
                            + bytes.length + ")");
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
        this.bytesCharset = charset;
    }

    TextContentBytes(ByteArrayOutputStream cmdiBuffer, Charset charset) {
        bytes = cmdiBuffer.toByteArray();
        offset = 0;
        length = bytes.length;
        this.bytesCharset = charset;
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
        CharsetDecoder cd = bytesCharset.newDecoder();
        ByteBuffer in = ByteBuffer.wrap(bytes, offset, length);
        CharBuffer out = CharBuffer.allocate(1024);
        while (in.hasRemaining()) {
            cd.decode(in, out, true);
            builder.append(out.array(), 0, out.position());
            ((Buffer)out).position(0);
        }
    }

    @Override
    public String toString() {
        return new String(bytes, offset, length, bytesCharset);
    }
}
