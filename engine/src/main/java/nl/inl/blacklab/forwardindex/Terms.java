package nl.inl.blacklab.forwardindex;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.collections.api.set.primitive.MutableIntSet;

import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/** Keeps a list of unique terms and their sort positions. */
@ThreadSafe
public interface Terms {

    /** The value to use meaning "no term" (e.g. if the document ends) */
    int NO_TERM = -1;

    /** Charset we use for serializing terms. */
    Charset TERMS_CHARSET = StandardCharsets.UTF_8;

    /**
     * Get the existing index number of a term, or add it to the term list and
     * assign it a new index number.
     *
     * In index mode, this is fast. In search mode, this is slower, because we have
     * to do a binary search through the memory-mapped terms file. However, this is
     * only done rarely.
     *
     * @param term the term to get the index number for
     * @return the term's index number, or -1 if not found
     */
    int indexOf(String term);

    /**
     * Get the index number(s) of terms matching a string.
     *
     * This is used in search mode when translating queries into NFAs. Depending on
     * case-sensitivity settings, a single term string may match multiple terms.
     *
     * @param results (out) index numbers for the matching term(s)
     * @param term the term to get the index number for
     * @param sensitivity compare sensitively? (case-sensitivity currently switches both case-
     *         and diacritics-sensitivity)
     */
    void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity);

    /**
     * Get a term by id. Only works in search mode.
     *
     * @param id the term id
     * @return the corresponding term
     */
    String get(int id);

    /**
     * @return the number of terms in this object
     */
    int numberOfTerms();

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
     * @param termId the term ids
     * @param sortOrder the sort positions
     * @param sensitivity whether we want the sensitive or insensitive sort positions
     */
    default void toSortOrder(int[] termId, int[] sortOrder, MatchSensitivity sensitivity) {
        for (int i = 0; i < termId.length; i++) {
            sortOrder[i] = idToSortPosition(termId[i], sensitivity);
        }
    }

    /**
     * Compare two terms (from their term ids) based on their sort positions
     *
     * @param termId1 id of the first term
     * @param termId2 id of the second term
     * @param sensitivity whether we want to compare sensitively or insensitively
     * @return the comparison result (negative if term1 < term2, zero if equal,
     *         positive if term1 > term2)
     */
    default int compareSortPosition(int termId1, int termId2, MatchSensitivity sensitivity) {
        return Integer.compare(idToSortPosition(termId1, sensitivity), idToSortPosition(termId2, sensitivity));
    }

    /**
     * Check if two terms are considered equal for the given sensitivity.
     *
     * @param termIds term id
     * @param sensitivity how to compare the terms
     * @return true if the terms are equal
     */
    boolean termsEqual(int[] termIds, MatchSensitivity sensitivity);

    /**
     * We have snippets with segment-specific term ids; convert them to global term ids.
     *
     * Note that with external forward index, there is no such thing as segment-specific term ids,
     * there's only global term ids. So in this case, this method should just return the input.
     *
     * @param ord segment these snippets came from
     * @param segmentResults snippets with segment-specific term ids
     * @return segments with global term ids
     */
    default List<int[]> segmentIdsToGlobalIds(int ord, List<int[]> segmentResults) {
        List<int[]> results = new ArrayList<>();
        for (int[] snippet: segmentResults) {
            results.add(segmentIdsToGlobalIds(ord, snippet));
        }
        return results;
    }

    /**
     * We have a snippet with segment-specific term ids; convert it to global term ids.
     *
     * Note that with external forward index, there is no such thing as segment-specific term ids,
     * there's only global term ids. So in this case, this method should just return the input.
     *
     * @param ord segment these snippets came from
     * @param segmentResults snippets with segment-specific term ids
     * @return segments with global term ids
     */
    int[] segmentIdsToGlobalIds(int ord, int[] segmentResults);

    /**
     * Convert a single seggment-specific term id to the corresponding global term id.
     *
     * @param ord segment these snippets came from
     * @param segmentTermId segment-specific term id
     * @return segments global term id
     */
    int segmentIdToGlobalId(int ord, int segmentTermId);
}
