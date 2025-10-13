package nl.inl.blacklab.codec;

import java.io.IOException;

/** A codec for blocks in the content store. */
public interface ContentStoreBlockCodec {

    interface Encoder extends AutoCloseable {
        /** Encode and return a new byte buffer.
         *
         * @param input value to encode
         * @param offset starting offset in the value to encode
         * @param length number of characters from value to encode
         * @return encoded buffer
         */
        byte[] encode(String input, int offset, int length) throws IOException;

        /** Encode in provided buffer.
         *
         * @param input value to encode
         * @param offset starting offset in the value to encode
         * @param length number of characters from value to encode
         * @param encoded output buffer
         * @param encodedOffset where to start writing in the output buffer
         * @param encodedMaxLength maximum number of bytes to write in the output buffer
         * @return compressed data length, or -1 if not enough buffer space
         */
        int encode(String input, int offset, int length, byte[] encoded, int encodedOffset, int encodedMaxLength) throws IOException;

        @Override
        void close();
    }

    interface Decoder extends AutoCloseable {

        /**
         * Decode a block to a string.
         *
         * @param buffer buffer containing block to decode
         * @param offset start of the block in the buffer
         * @param length length of the block in the buffer
         * @return decoded block as a string
         */
        String decode(byte[] buffer, int offset, int length) throws IOException;

        /**
         * Decode a block to a byte array.
         *
         * @param buffer buffer containing block to decode
         * @param offset start of the block in the buffer
         * @param length length of the block in the buffer
         * @param decoded where to decode the block to
         * @param decodedOffset where to start writing the decoded block
         * @param decodedMaxLength max. size of the decoded block
         * @return number of bytes in decoded block, or -1 if the buffer size was too small
         */
        int decode(byte[] buffer, int offset, int length, byte[] decoded, int decodedOffset, int decodedMaxLength) throws IOException;

        @Override
        void close();
    }

    static ContentStoreBlockCodec fromCode(byte code) {
        return switch (code) {
            case 0 -> ContentStoreBlockCodecUncompressed.INSTANCE;
            case 1 -> ContentStoreBlockCodecZlib.INSTANCE;
            default -> throw new IllegalArgumentException("Unknown block codec with code " + code);
        };
    }

    Encoder getEncoder();

    Decoder getDecoder();

    byte getCode();
}
