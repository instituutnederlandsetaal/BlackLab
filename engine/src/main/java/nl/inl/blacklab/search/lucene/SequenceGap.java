package nl.inl.blacklab.search.lucene;

/**
 * Allowable gap size between parts of a sequence.
 */
public record SequenceGap(int minSize, int maxSize) {

    public static final SequenceGap NONE = fixed(0);

    public static final SequenceGap ANY = atLeast(0);

    public static SequenceGap atLeast(int minSize) {
        return new SequenceGap(minSize, BLSpans.MAX_UNLIMITED);
    }

    public static SequenceGap atMost(int maxSize) {
        return new SequenceGap(0, maxSize);
    }

    public static SequenceGap fixed(int size) {
        return new SequenceGap(size, size);
    }

    public static SequenceGap variable(int minSize, int maxSize) {
        return new SequenceGap(minSize, maxSize);
    }

    public boolean isFixed() {
        return minSize == maxSize;
    }

    @Override
    public String toString() {
        return minSize + "-" + maxSize;
    }

}
