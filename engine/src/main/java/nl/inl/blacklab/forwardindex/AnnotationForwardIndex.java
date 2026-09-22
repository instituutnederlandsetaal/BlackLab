package nl.inl.blacklab.forwardindex;

/**
 * Forward index for a single annotation.
 * <p>
 * Implementations may operate on the entire index (with global doc ids)
 * or on a single segment (with segment doc ids).
 */
public interface AnnotationForwardIndex {
    int[][] retrieveParts(int docId, int[] starts, int[] ends);

    int[] retrievePart(int docId, int start, int end);

    /** Get length of document in tokens from a forward index.
     *
     * The document length should be the same for all annotations on the same field, of course.
     *
     * The "extra closing token" that is added to the end of the document (for punctuation and closing tags
     * after the last word) is NOT included in the length.
     *
     * @param docId docId of document to get length for
     * @return doc length in tokens
     */
    long docLength(int docId);

    Terms terms();

    String getLuceneFieldName();
}
