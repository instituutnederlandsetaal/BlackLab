package nl.inl.blacklab.tools.frequency;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Precalculated hashcode for group id, to save time while grouping and sorting.
 */
class GroupIdHash implements Comparable<GroupIdHash>, Serializable {
    private final int ngramSize;
    private final int[] tokenIds;
    private final int[] tokenSortPositions;
    private final String[] metadataValues;
    private final int hash;

    /**
     * @param tokenSortPositions sort position for each token in the group id
     * @param metadataValues     relevant metadatavalues
     * @param metadataValuesHash since many tokens per document, precalculate md hash for that thing
     */
    public GroupIdHash(int ngramSize, int[] tokenIds, int[] tokenSortPositions, String[] metadataValues, int metadataValuesHash) {
        this.ngramSize = ngramSize;
        this.tokenIds = tokenIds;
        this.tokenSortPositions = tokenSortPositions;
        this.metadataValues = metadataValues;
        hash = Arrays.hashCode(tokenSortPositions) ^ metadataValuesHash;
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
        return ((GroupIdHash) obj).hash == this.hash &&
                Arrays.equals(((GroupIdHash) obj).tokenSortPositions, this.tokenSortPositions) &&
                Arrays.equals(((GroupIdHash) obj).metadataValues, this.metadataValues);
    }

    @Override
    public int compareTo(GroupIdHash other) {
        int cmp = Integer.compare(hash, other.hash);
        if (cmp == 0)
            // flatmap for comparison
            cmp = Arrays.compare(tokenSortPositions, other.tokenSortPositions);
        if (cmp == 0)
            cmp = Arrays.compare(metadataValues, other.metadataValues);
        return cmp;
    }

    public int getNgramSize() {
        return ngramSize;
    }
}
