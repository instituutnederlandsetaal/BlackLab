package nl.inl.blacklab.search.lucene;

/**
 * Ways to filter one list of spans using another list of spans.
 * The producer spans are filtered based on their positions relative to the filter spans.
 * So CONTAINING will return producer spans that contain one (or more) filter spans.
 * Below, we describe the test for a producer hit to be included, i.e. "contains a filter hit".
 */
public enum SpanFilter {

    /**
     * Contains a filter hit
     */
    CONTAINING,

    /**
     * Contain a filter hit with the same start position
     */
    CONTAINING_AT_START,

    /**
     * Contain a filter hit with the same end position
     */
    CONTAINING_AT_END,

    /**
     * Is contained within a filter hit
     */
    WITHIN,

    /**
     * Starts at a filter hit
     */
    STARTS_AT,

    /**
     * Ends at a filter hit
     */
    ENDS_AT,

    /**
     * Exactly matches a filter hit (i.e. same as token-level AND)
     */
    MATCHES,

    /**
     * Overlaps a filter hit (i.e. they share at least one token)
     */
    HAS_OVERLAP;

    public static SpanFilter fromStringValue(String s) {
        for (SpanFilter op: values()) {
            if (op.name().equalsIgnoreCase(s)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown operation: " + s);
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
