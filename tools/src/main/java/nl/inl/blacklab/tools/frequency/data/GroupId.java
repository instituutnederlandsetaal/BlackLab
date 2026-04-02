package nl.inl.blacklab.tools.frequency.data;

import java.io.Serializable;
import java.util.Arrays;

import nl.inl.blacklab.tools.frequency.data.document.DocumentMetadata;

/**
 * Group id with precalculated hash to save time while grouping and sorting.
 */
public record GroupId(int[] tokens, int[] sorting, int[] metadata, int hash)
        implements Comparable<GroupId>, Serializable {

    public GroupId(final int[] tokens, final int[] sorting, final DocumentMetadata meta) {
        this(tokens, sorting, meta.values(), Arrays.hashCode(sorting) ^ meta.hash());
    }

    @Override
    public int hashCode() {
        return hash;
    }

    // Assume we only call with this class
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(final Object obj) {
        final var other = (GroupId) obj;
        return other.hash == hash && Arrays.equals(other.sorting, sorting) && Arrays.equals(other.metadata, metadata);
    }

    @Override
    public int compareTo(final GroupId other) {
        int cmp = Integer.compare(hash, other.hash);
        if (cmp == 0)
            cmp = Arrays.compare(sorting, other.sorting);
        if (cmp == 0)
            cmp = Arrays.compare(metadata, other.metadata);
        return cmp;
    }
}
