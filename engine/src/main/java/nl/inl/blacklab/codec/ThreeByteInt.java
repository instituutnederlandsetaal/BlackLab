package nl.inl.blacklab.codec;

import java.io.IOException;

/**
 * Provides a way to serialize a 3-byte signed integer.
 *
 * Careful: the write() method does NOT check if the given
 * integer is within MIN_VALUE and MAX_VALUE. You should make sure
 * of this.
 */
public class ThreeByteInt {

    private ThreeByteInt() {
    }

    @FunctionalInterface
    public interface ByteOutput {
        void writeByte(byte b) throws IOException;
    }

    @FunctionalInterface
    public interface ByteInput {
        byte readByte() throws IOException;
    }

    /**
     * Minimum value that can be stored in a three-byte int (-2^23).
     */
    public static int MIN_VALUE = -8388608;

    /**
     * Maximum value that can be stored in a three-byte int (2^23-1).
     */
    public static int MAX_VALUE = 8388607;

    public static void write(ByteOutput out, int value) throws IOException {
        out.writeByte((byte) (value >> 16));
        out.writeByte((byte) (value >> 8));
        out.writeByte((byte) value);
    }

    public static int read(ByteInput in) throws IOException {
        int v = (((in.readByte() & 0xFF) << 16) | ((in.readByte() & 0xFF) << 8) | (in.readByte() & 0xFF));
        // Is the three-byte integer's sign bit set?
        if ((v & 0x800000) != 0) {
            // Yes. Make the most significant byte 0xFF to get the correct 4-byte integer.
            v |= 0xFF000000;
        }
        return v;
    }
}
