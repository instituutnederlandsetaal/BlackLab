package nl.inl.blacklab.forwardindex;

import java.util.List;

import net.jcip.annotations.NotThreadSafe;

/**
 * Provides read access to the forward indexes in a single Lucene index segment.
 *
 * Implementations are not intended to be threadsafe, but to be used by a single
 * thread.
 */
@NotThreadSafe
public interface ForwardIndexSegmentReader {

    /** Retrieve parts of a document from a forward index.
     *
     * @param luceneField lucene field to retrieve snippet from
     * @param docId segment-local docId of document to retrieve snippet from
     * @param starts starting token positions
     * @param ends ending token positions
     * @return snippets (with segment-local term ids)
     */
    List<int[]> retrieveParts(String luceneField, int docId, int[] starts, int[] ends);

    /** Retrieve a single part of a document from a forward index.
     *
     * @param luceneField lucene field to retrieve snippet from
     * @param docId segment-local docId of document to retrieve snippet from
     * @param start starting token positions
     * @param end ending token positions
     * @return snippets (with segment-local term ids)
     */
    int[] retrievePart(String luceneField, int docId, int start, int end);

    /** Get length of document in tokens from a forward index.
     *
     * The document length should be the same for all annotations on the same field, of course.
     *
     * The "extra closing token" that is added to the end of the document (for punctuation and closing tags
     * after the last word) is included in the length.
     *
     * @param luceneField lucene field to read forward index from
     * @param docId segment-local docId of document to get length for
     * @return doc length in tokens (including the "extra closing token")
     */
    long docLength(String luceneField, int docId);

    /**
     * Get a 
     * @param luceneField
     * @return
     */
    TermsSegmentReader terms(String luceneField);
}
