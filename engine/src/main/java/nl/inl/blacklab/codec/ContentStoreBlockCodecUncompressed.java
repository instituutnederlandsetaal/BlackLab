package nl.inl.blacklab.codec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A codec for blocks in the content store that performs no compression but just stores UTF-8 data as-is.
 */
public class ContentStoreBlockCodecUncompressed implements ContentStoreBlockCodec {

    public static final ContentStoreBlockCodec INSTANCE = new ContentStoreBlockCodecUncompressed();

    private static final Decoder DECODER = new Decoder() {
        @Override
        public void close() {
            // nothing to do, this is a reusable singleton.
        }

        @Override
        public String decode(byte[] buffer, int offset, int length) throws IOException {
            return new String(buffer, offset, length, StandardCharsets.UTF_8);
        }

        @Override
        public int decode(byte[] buffer, int offset, int length, byte[] decoded, int decodedOffset, int decodedMaxLength) throws IOException {
            if (length > decodedMaxLength)
                throw new IOException("Insufficient buffer space available");
            System.arraycopy(buffer, offset, decoded, decodedOffset, length);
            return length;
        }
    };

    private static final Encoder ENCODER = new Encoder() {
        @Override
        public void close() {
            // nothing to do, this is a reusable singleton.
        }

        @Override
        public byte[] encode(String input, int offset, int length) {
            return input.substring(offset, offset + length).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public int encode(String input, int offset, int length, byte[] encoded, int encodedOffset,
                int encodedMaxLength) {
            byte[] bytes = encode(input, offset, length);
            if (bytes.length <= encodedMaxLength) {
                // This fits in the buffer; copy it
                System.arraycopy(bytes, 0, encoded, encodedOffset, bytes.length);
            }
            // return the actual length; the called can compare to the max it provided to know if we were succesful
            return bytes.length;
        }
    };

    private ContentStoreBlockCodecUncompressed() {
    }

    @Override
    public Decoder getDecoder() {
        return DECODER;
    }

    @Override
    public Encoder getEncoder() {
        return ENCODER;
    }

    @Override
    public byte getCode() {
        return 0;
    }
}
