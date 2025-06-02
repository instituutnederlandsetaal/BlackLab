package nl.inl.blacklab.codec;

/**
 * How the tokens in a document are encoded in the tokens file.
 * This allows us to add alternative encodings over time, e.g. to deal with
 * sparse annototions, use variable-length token ids, etc.
 * Every document in the index has an entry in the tokens index file, basically a header containing:
 * - offset in actual tokens file
 * - doc length
 * - codec (this) 
 * - codec parameter (usually 0, but can be set depending on codec).
 */
public enum TokensCodec {
    /** Simplest possible encoding, one 4-byte integer per token. */
    VALUE_PER_TOKEN((byte) 1),

    /** All our tokens have the same value. Stores only that value (as Integer). */
    ALL_TOKENS_THE_SAME((byte) 2);

    /** How we'll write this encoding to the tokens index file. */
    public final byte code;

    TokensCodec(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static TokensCodec fromCode(byte code) {
        for (TokensCodec t: values()) {
            if (t.code == code)
                return t;
        }
        throw new IllegalArgumentException("Unknown tokens codec: " + code);
    }

    public enum VALUE_PER_TOKEN_PARAMETER {
        BYTE((byte) 0),
        SHORT((byte) 1),
        THREE_BYTES((byte) 2),
        INT((byte) 3);

        public final byte code;

        VALUE_PER_TOKEN_PARAMETER(byte code) {
            this.code = code;
        }

        public static VALUE_PER_TOKEN_PARAMETER fromCode(byte code) {
            for (VALUE_PER_TOKEN_PARAMETER t: values()) {
                if (t.code == code)
                    return t;
            }
            throw new IllegalArgumentException("Unknown payload value for VALUE_PER_TOKEN: " + code);
        }
    }
}
