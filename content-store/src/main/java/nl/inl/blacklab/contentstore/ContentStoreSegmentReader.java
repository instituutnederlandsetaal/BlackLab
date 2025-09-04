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
     * Get field value as bytes.
     *
     * @param docId document id
     * @param luceneField field to get
     * @return requested parts
     */
    byte[] getBytes(int docId, String luceneField);
}
