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

    long docLength(int docId);

    Terms terms();

    String getLuceneFieldName();
}
