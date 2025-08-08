package nl.inl.blacklab.forwardindex;

import java.util.ArrayList;
import java.util.List;

import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/** Keeps a list of unique terms and their sort positions. */
@ThreadSafe
public interface Terms {

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

}
