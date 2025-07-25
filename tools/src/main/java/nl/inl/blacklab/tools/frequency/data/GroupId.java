package nl.inl.blacklab.tools.frequency.data;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Group id, with a precalculated hashcode to save time while grouping and sorting.
 */
public final class GroupId implements Comparable<GroupId>, Serializable {
    private final int[] tokenIds;
    private final int[] tokenSortPositions;
    private final int[] metadataValues;
    private final int hash;

    /**
     * @param tokenSortPositions sort position for each token in the group id
     */
    public GroupId(final int[] tokenIds, final int[] tokenSortPositions,
            final DocumentMetadata meta) {
        this.tokenIds = tokenIds;
        this.tokenSortPositions = tokenSortPositions;
        this.metadataValues = meta.values();
        hash = Arrays.hashCode(tokenSortPositions) ^ meta.hash();
    }

    public int[] getTokenIds() {
        return tokenIds;
    }

    public int[] getMetadataValues() {
        return metadataValues;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    // Assume only called with other instances of IdHash (faster for large groupings)
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(final Object obj) {
        return ((GroupId) obj).hash == hash &&
                Arrays.equals(((GroupId) obj).tokenSortPositions, tokenSortPositions) &&
                Arrays.equals(((GroupId) obj).metadataValues, metadataValues);
    }

    @Override
    public int compareTo(final GroupId other) {
        return Integer.compare(hash, other.hash);
    }
}
