package nl.inl.blacklab.tools.frequency;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Precalculated hashcode for group id, to save time while grouping and sorting.
 */
class GroupIdHash implements Comparable<GroupIdHash>, Serializable {
    private final int ngramSize;

    /** Tokens as (segment-local) term ids */
    private final int[] tokenIds;

    /** Tokens as (segment-local) sort positions */
    private final int[] tokenSortPositions;

    private final String[] metadataValues;

    /** Precalculated hash code. */
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
        if (((GroupIdHash) obj).hash != this.hash)
            return false;
        if (!Arrays.deepEquals(((GroupIdHash) obj).metadataValues, this.metadataValues))
            return false;
        return Arrays.equals(((GroupIdHash) obj).tokenSortPositions, this.tokenSortPositions);
    }

    @Override
    public int compareTo(GroupIdHash other) {
        int cmp = Integer.compare(hash, other.hash);
        if (cmp == 0)
            cmp = Arrays.compare(tokenSortPositions, other.tokenSortPositions);
        if (cmp == 0)
            cmp = Arrays.compare(metadataValues, other.metadataValues);
        return cmp;
    }

    public int getNgramSize() {
        return ngramSize;
    }

    public int[] getTokenIds() {
        return tokenIds;
    }

    public GroupIdHash toGlobalTermIds(BlackLabIndex index, LeafReaderContext lrc, List<Terms> hitProperties) {
        int[] globalTermIds = new int[tokenIds.length];
        int[] globalSortPositions = new int[tokenIds.length];
        for (int i = 0; i < tokenIds.length; i++) {
            // Convert segment-local term ids to global term ids
            // (this is necessary because the same term can have different ids in different segments)
            Terms terms = hitProperties.get(i);
            globalTermIds[i] = terms.toGlobalTermId(lrc, tokenIds[i]);
            globalSortPositions[i] = terms.idToSortPosition(globalTermIds[i],
                    MatchSensitivity.INSENSITIVE);
        }
        return new GroupIdHash(ngramSize, globalTermIds, globalSortPositions,
                metadataValues, Arrays.hashCode(metadataValues));
    }
}
