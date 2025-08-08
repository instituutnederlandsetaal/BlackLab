package nl.inl.blacklab.tools.frequency;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.BlackLabCodecUtil;
import nl.inl.blacklab.codec.BlackLabPostingsReader;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Precalculated hashcode for group id, to save time while grouping and sorting.
 */
class GroupIdHash implements Comparable<GroupIdHash>, Serializable {
    private final int ngramSize;

    /** Tokens as (segment-local) term ids */
    private final int[] tokenIds;

    /** Tokens as (segment-local) sort positions */
    private final int[] tokenSortPositions;

    /**
     * The tokens as strings.
     * <p>
     * Set after merging per-segment results into global results.
     * tokenIds and tokenSortPositions are null in this case.
     */
    private final String[] tokens;

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
        this.tokens = null;
        this.metadataValues = metadataValues;
        hash = Arrays.hashCode(tokenSortPositions) ^ metadataValuesHash;
    }

    /**
     * Construct the version with token strings.
     *
     * @param tokens token term for each token in the group id
     * @param metadataValues relevant metadatavalues
     * @param metadataValuesHash since many tokens per document, precalculate md hash for that thing
     */
    public GroupIdHash(int ngramSize, String[] tokens, String[] metadataValues, int metadataValuesHash) {
        this.ngramSize = ngramSize;
        this.tokenIds = null;
        this.tokenSortPositions = null;
        this.tokens = tokens;
        this.metadataValues = metadataValues;
        hash = Arrays.hashCode(this.tokens) ^ metadataValuesHash;
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
        if (((GroupIdHash) obj).hash != this.hash)
            return false;
        if (!Arrays.deepEquals(((GroupIdHash) obj).metadataValues, this.metadataValues))
            return false;
        if (tokens != null) {
            // String form
            return Arrays.equals(tokens, ((GroupIdHash) obj).tokens);
        } else {
            // Segment sort order form
            return Arrays.equals(((GroupIdHash) obj).tokenSortPositions, this.tokenSortPositions);
        }
    }

    @Override
    public int compareTo(GroupIdHash other) {
        int cmp = Integer.compare(hash, other.hash);
        if (cmp == 0) {
            if (tokens != null) {
                // String form
                cmp = Arrays.compare(tokens, other.tokens);
            } else {
                // Segment sort order form
                cmp = Arrays.compare(tokenSortPositions, other.tokenSortPositions);
            }
        }
        if (cmp == 0)
            cmp = Arrays.compare(metadataValues, other.metadataValues);
        return cmp;
    }

    public int getNgramSize() {
        return ngramSize;
    }

    /** Convert term ids to their string representation.
     * Done when merging per-segment results into global results.
     */
    public GroupIdHash termIdsToStrings(LeafReaderContext lrc, List<Annotation> hitProperties) {
        if (tokens != null) {
            // Already converted to strings
            return this;
        }
        if (tokenIds == null || tokenSortPositions == null) {
            throw new IllegalStateException("Cannot convert term ids to strings, no term ids available");
        }
        String[] tokenStrings = new String[tokenIds.length];
        BlackLabPostingsReader postingsReader = BlackLabCodecUtil.getPostingsReader(lrc);
        for (int i = 0; i < tokenIds.length; i++) {
            String luceneFieldName = hitProperties.get(i).forwardIndexSensitivity().luceneField();
            try {
                int tokensSegmentTermId = tokenIds[i];
                tokenStrings[i] = tokensSegmentTermId >= 0 ?
                        postingsReader.terms(luceneFieldName).reader().get(tokensSegmentTermId) :
                        null;
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        }
        return new GroupIdHash(ngramSize, tokenStrings, metadataValues, Arrays.hashCode(metadataValues));
    }
}
