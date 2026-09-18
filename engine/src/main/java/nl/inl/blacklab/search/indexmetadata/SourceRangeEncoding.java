package nl.inl.blacklab.search.indexmetadata;

/** Stable schema constants for exact token source ranges stored in term-vector payloads. */
public final class SourceRangeEncoding {

    /** Index-level writer/storage contract. */
    public static final String INDEX_FLAG = "token_offset_storage";

    public static final String VERSION = "source-range-vectors-v2";

    public static final String TERM = "r";

    public static final int PAYLOAD_LENGTH = Integer.BYTES * 2;

    public static final int DOC_FLAG_XML = 1;

    public static final int DOC_FLAG_SOURCE_RANGE_VECTORS = 1 << 1;

    private SourceRangeEncoding() {
    }
}
