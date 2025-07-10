package nl.inl.blacklab.tools.frequency.data;

import java.util.Arrays;

public class AnnotationGroupId implements Comparable<AnnotationGroupId> {
    private final int[] tokens;
    private final int hash;

    public AnnotationGroupId(final int[] tokens) {
        this.hash = Arrays.hashCode(tokens);
        this.tokens = tokens;
    }

    public int[] getTokens() {
        return tokens;
    }

    @Override
    public final int compareTo(final AnnotationGroupId other) {
        return Integer.compare(hash, other.hash);
    }
}
