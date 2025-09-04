package nl.inl.blacklab.codec.tokens;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

public enum TokenValueType {
    BYTE((byte) 0, Byte.BYTES, Byte.MIN_VALUE, Byte.MAX_VALUE),
    SHORT((byte) 1, Short.BYTES, Short.MIN_VALUE, Short.MAX_VALUE),
    THREE_BYTES((byte) 2, ThreeByteInt.BYTES, ThreeByteInt.MIN_VALUE, ThreeByteInt.MAX_VALUE),
    INT((byte) 3, Integer.BYTES, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public final byte code;

    private final int sizeBytes;

    private final int minValue;

    private final int maxValue;

    TokenValueType(byte code, int sizeBytes, int minValue, int maxValue) {
        this.code = code;
        this.sizeBytes = sizeBytes;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public static TokenValueType fromCode(byte code) {
        for (TokenValueType t: values()) {
            if (t.code == code)
                return t;
        }
        throw new IllegalArgumentException("Unknown payload value for VALUE_PER_TOKEN: " + code);
    }

    public static TokenValueType choose(int min, int max) {
        if (min >= Byte.MIN_VALUE && max <= Byte.MAX_VALUE)
            return TokenValueType.BYTE;
        else if (min >= Short.MIN_VALUE && max <= Short.MAX_VALUE)
            return TokenValueType.SHORT;
        else if (min >= ThreeByteInt.MIN_VALUE && max <= ThreeByteInt.MAX_VALUE)
            return TokenValueType.THREE_BYTES;
        else
            return TokenValueType.INT;
    }

    public void write(int token, IndexOutput outTokensFile) throws IOException {
        switch (this) {
        case BYTE:
            outTokensFile.writeByte((byte) token);
            break;
        case SHORT:
            outTokensFile.writeShort((short) token);
            break;
        case THREE_BYTES:
            ThreeByteInt.write(outTokensFile::writeByte, token);
            break;
        case INT:
            outTokensFile.writeInt((int) token);
            break;
        }
    }

    public int read(ByteBuffer buffer) throws IOException {
        return switch (this) {
            case BYTE -> buffer.get();
            case SHORT -> buffer.getShort();
            case THREE_BYTES -> ThreeByteInt.read(buffer::get);
            case INT -> buffer.getInt();
        };
    }

    public int read(IndexInput tokensFile) throws IOException {
        return switch (this) {
            case BYTE -> tokensFile.readByte();
            case SHORT -> tokensFile.readShort();
            case THREE_BYTES -> ThreeByteInt.read(tokensFile::readByte);
            case INT -> tokensFile.readInt();
        };
    }

    public int sizeBytes() {
        return sizeBytes;
    }

    public int minValue() {
        return minValue;
    }

    public int maxValue() {
        return maxValue;
    }
}
