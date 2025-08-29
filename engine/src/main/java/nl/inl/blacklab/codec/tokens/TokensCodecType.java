package nl.inl.blacklab.codec.tokens;

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
public enum TokensCodecType {
    /** Simplest possible encoding, one 4-byte integer per token. */
    VALUE_PER_TOKEN((byte) 1),

    /** All our tokens have the same value. Stores only that value (as Integer). */
    ALL_TOKENS_THE_SAME((byte) 2),

    /** Tokens are run-length encoded in blocks, with the offset of each block stored. */
    RUN_LENGTH_ENCODING((byte) 3);

    /** How we'll write this encoding to the tokens index file. */
    public final byte code;

    TokensCodecType(byte code) {
        this.code = code;
    }

    public static TokensCodecType fromCode(byte code) {
        for (TokensCodecType t: values()) {
            if (t.code == code)
                return t;
        }
        throw new IllegalArgumentException("Unknown tokens codec: " + code);
    }
}
