package nl.inl.blacklab.contentstore;

import net.jcip.annotations.NotThreadSafe;

/**
 * Provides read access to the content stores in a single Lucene index segment.
 *
 * Implementations are not intended to be threadsafe, but to be used by a single
 * thread.
 */
@NotThreadSafe
public interface ContentStoreSegmentReader {

    /**
     * Get the entire field value.
     *
     * @param docId document id
     * @param luceneField field to get
     * @return field value
     */
    String getValue(int docId, String luceneField);

    /**
     * Get part of the field value.
     *
     * @param docId document id
     * @param luceneField field to get
     * @param start first character to get. Must be zero or greater.
     * @param end character after the last character to get, or -1 for <code>value.length()</code>.
     * @return requested part
     */
    String getValueSubstring(int docId, String luceneField, int start, int end);

    /**
     * Get several parts of the field value.
     *
     * @param docId document id
     * @param luceneField field to get
     * @param start positions of the first character to get. Must all be zero or greater.
     * @param end positions of the character after the last character to get, or -1 for <code>value.length()</code>.
     * @return requested parts
     */
    String[] getValueSubstrings(int docId, String luceneField, int[] start, int[] end);

    /**
     * Finds the length in characters of a stored value.
     *
     * @param docId document id
     * @param luceneField field to get length for
     * @return length of the value in characters
     */
    int valueLength(int docId, String luceneField);
}
