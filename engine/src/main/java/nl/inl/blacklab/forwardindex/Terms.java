package nl.inl.blacklab.forwardindex;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Read terms for a single field.
 * Should be thread-safe.
 */
public interface Terms {

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
     * Find the sort position for a term.
     *
     * NOTE: this is relatively expensive, so try to avoid calling it
     * in performance-critical code. Could be optimized by a bunch of precalculations,
     * but not worth it for now.
     *
     * @param term the term to find
     * @param sensitivity how to compare the term (case-sensitive, diacritics-sensitive, etc.)
     * @return the sort position of the term, or -1 if not found
     */
    int termToSortPosition(String term, MatchSensitivity sensitivity);

    /**
     * Convert an array of term ids to sort positions
     *
     * @param termIds the term ids
     * @param sortOrder the sort positions
     * @param sensitivity whether we want the sensitive or insensitive sort positions
     */
    default void idsToSortOrder(int[] termIds, int[] sortOrder, MatchSensitivity sensitivity) {
        // optimize?
        for (int i = 0; i < termIds.length; i++) {
            sortOrder[i] = idToSortPosition(termIds[i], sensitivity);
        }
    }

    int indexOf(String word, MatchSensitivity sensitivity);

    /**
     * Convert an array of segment term ids to global term ids.
     *
     * @param segmentTermIds segment term ids
     */
    default void convertToGlobalTermIds(LeafReaderContext lrc, int[] segmentTermIds) {
        throw new UnsupportedOperationException();
    }

    default int toGlobalTermId(LeafReaderContext lrc, int tokenId) {
        throw new UnsupportedOperationException();
    }
}
