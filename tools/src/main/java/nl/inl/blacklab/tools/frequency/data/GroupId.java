package nl.inl.blacklab.tools.frequency.data;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Group id, with a precalculated hashcode to save time while grouping and sorting.
 */
public final class GroupId implements Comparable<GroupId>, Serializable {
    private final int ngramSize;
    private final int[] tokenIds;
    private final int[] tokenSortPositions;
    private final String[] metadataValues;
    private final int hash;

    /**
     * @param tokenSortPositions sort position for each token in the group id
     */
    public GroupId(int ngramSize, int[] tokenIds, int[] tokenSortPositions, DocumentMetadata meta) {
        this.ngramSize = ngramSize;
        this.tokenIds = tokenIds;
        this.tokenSortPositions = tokenSortPositions;
        this.metadataValues = meta.values();
        hash = Arrays.hashCode(tokenSortPositions) ^ meta.hash();
    }

    public int[] getTokenIds() {
        return tokenIds;
    }

    public String[] getMetadataValues() {
        return metadataValues;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    // Assume only called with other instances of IdHash (faster for large groupings)
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object obj) {
        return ((GroupId) obj).hash == this.hash &&
                Arrays.equals(((GroupId) obj).tokenSortPositions, this.tokenSortPositions) &&
                Arrays.equals(((GroupId) obj).metadataValues, this.metadataValues);
    }

    @Override
    public int compareTo(GroupId other) {
        return Integer.compare(hash, other.hash);
    }

    public int getNgramSize() {
        return ngramSize;
    }
}
