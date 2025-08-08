package nl.inl.blacklab.forwardindex;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Read terms for a single field from an index segment.
 * Should be thread-safe.
 */
public interface TermsSegment {

    /**
     * Find the term id for a term string.
     *
     * Fairly slow operation, but not used very often.
     *
     * @param term the term to get the index number for
     * @return the term's index number, or -1 if not found
     */
    int indexOf(String term);

    /**
     * Get the term string for a term id.
     *
     * @param id term id
     * @return term string
     */
    String get(int id);

    /**
     * @return the number of terms in this object
     */
    int numberOfTerms();

    /**
     * Check if two terms are considered equal for the given sensitivity.
     * @param termIds term id
     * @param sensitivity how to compare the terms
     * @return true if the terms are equal
     */
    default boolean termsEqual(int[] termIds, MatchSensitivity sensitivity) {
        if (termIds.length < 2)
            return true;
        // optimize?
        int expected = idToSortPosition(termIds[0], sensitivity);
        for (int termIdIndex = 1; termIdIndex < termIds.length; ++termIdIndex) {
            int cur = idToSortPosition(termIds[termIdIndex], sensitivity);
            if (cur != expected)
                return false;
        }
        return true;
    }

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
    default void toSortOrder(int[] termIds, int[] sortOrder, MatchSensitivity sensitivity) {
        // optimize?
        for (int i = 0; i < termIds.length; i++) {
            sortOrder[i] = idToSortPosition(termIds[i], sensitivity);
        }
    }

    default int sortPositionFor(String term, MatchSensitivity sensitivity) {
        return idToSortPosition(indexOf(term), sensitivity);
    }
}
