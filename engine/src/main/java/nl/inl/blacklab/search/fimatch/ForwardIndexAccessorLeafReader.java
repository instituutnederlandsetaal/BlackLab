package nl.inl.blacklab.search.fimatch;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.forwardindex.TermsSegmentReader;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Allows the forward index matching subsystem to access the forward indices
 * in a single index segment (LeafReader).
 *
 * Not intended to be threadsafe, but to be used from e.g. Spans in a single thread.
 */
@NotThreadSafe
public interface ForwardIndexAccessorLeafReader {
    /**
     * Get a token source, which we can use to get tokens from a document for
     * different annotations.
     *
     * @param segmentDocId document id within this segment
     * @return the token source
     */
    ForwardIndexDocument getForwardIndexDoc(int segmentDocId);

    /**
     * Return the document length in tokens.
     *
     * NOTE: this does NOT include the extra closing token at the end.
     *
     * @param segmentDocId document id within this segment
     * @return document length in tokens
     */
    int getDocLength(int segmentDocId);

    /**
     * Get a chunk of tokens from a forward index
     *
     * NOTE: Returns chunks with *segment-local* term ids!
     *
     * @param annotIndex annotation to get tokens for
     * @param segmentDocId document id within this segment
     * @param start first token to get
     * @param end one more than the last token to get
     * @return chunk of tokens
     */
    int[] getChunkSegmentTermIds(int annotIndex, int segmentDocId, int start, int end);

    int getNumberOfAnnotations();

    TermsSegmentReader terms(int annotIndex);

    /**
     * Get a term string for a term id.
     *
     * @param annotIndex which annotation?
     * @param segmentTermId term id within the segment
     * @return term string
     */
    default String getTermString(int annotIndex, int segmentTermId) {
        return terms(annotIndex).get(segmentTermId);
    }

    /**
     * Check if a number of terms are considered equal for the given sensitivity.
     *
     * @param annotIndex which annotation?
     * @param segmentTermIds term ids to compare
     * @param sensitivity how to compare the terms
     * @return true if all the terms are equals
     */
    default boolean segmentTermsEqual(int annotIndex, int[] segmentTermIds, MatchSensitivity sensitivity) {
        return terms(annotIndex).termsEqual(segmentTermIds, sensitivity);
    }

    default int segmentTermIdToSortPosition(int annotIndex, int segmentTermId, MatchSensitivity sensitivity) {
        return terms(annotIndex).idToSortPosition(segmentTermId, sensitivity);
    }
}
