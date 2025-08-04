package nl.inl.blacklab.forwardindex;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Read terms for a single field from an index segment.
 * Should be thread-safe.
 */
public interface TermsSegmentReader {

    /**
     * Get the term string for a term id.
     *
     * @param id term id
     * @return term string
     */
    String get(int id);

    /**
     * Check if two terms are considered equal for the given sensitivity.
     * @param termIds term id
     * @param sensitivity how to compare the terms
     * @return true if the terms are equal
     */
    boolean termsEqual(int[] termIds, MatchSensitivity sensitivity);

    /**
     * Get the sort position for a term based on its term id
     *
     * @param id the term id
     * @param sensitivity whether we want the sensitive or insensitive sort position
     * @return the sort position
     */
    int idToSortPosition(int id, MatchSensitivity sensitivity);

    /**
     * Convert an array of term ids to sort positions
     *
     * @param termIds the term ids
     * @param sortOrder the sort positions
     * @param sensitivity whether we want the sensitive or insensitive sort positions
     */
    void toSortOrder(int[] termIds, int[] sortOrder, MatchSensitivity sensitivity);
}
