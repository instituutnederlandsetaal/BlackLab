package nl.inl.blacklab.forwardindex;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.BlackLabPostingsReader;
import nl.inl.blacklab.codec.ForwardIndexField;

/** A forward index interface for a single field in a segment.
 *
 * Implementations are not intended to be threadsafe, but to be used by a single
 * thread.
 */
public class FieldForwardIndex implements AnnotationForwardIndex {

    /** Our terms object */
    private Terms terms;

    public static AnnotationForwardIndex get(LeafReaderContext lrc, String luceneField) {
        return BlackLabPostingsReader.forSegment(lrc).forwardIndex(luceneField);
    }

    private final ForwardIndexSegmentReader forwardIndex;

    private final ForwardIndexField field;

    public FieldForwardIndex(ForwardIndexSegmentReader forwardIndex, ForwardIndexField field) {
        this.forwardIndex = forwardIndex;
        this.field = field;
    }

    /** Retrieve parts of a document from a forward index.
     *
     * @param docId segment-local docId of document to retrieve snippet from
     * @param starts starting token positions
     * @param ends ending token positions
     * @return snippets (with segment-local term ids)
     */
    @Override
    public int[][] retrieveParts(int docId, int[] starts, int[] ends) {
        return forwardIndex.retrieveParts(field, docId, starts, ends);
    }

    /** Retrieve a single part of a document from a forward index.
     *
     * @param docId segment-local docId of document to retrieve snippet from
     * @param start starting token positions
     * @param end ending token positions
     * @return snippets (with segment-local term ids)
     */
    @Override
    public int[] retrievePart(int docId, int start, int end) {
        return forwardIndex.retrievePart(field, docId, start, end);
    }

    /** Get length of document in tokens from a forward index.
     *
     * The document length should be the same for all annotations on the same field, of course.
     *
     * The "extra closing token" that is added to the end of the document (for punctuation and closing tags
     * after the last word) is included in the length.
     *
     * @param docId segment-local docId of document to get length for
     * @return doc length in tokens (including the "extra closing token")
     */
    @Override
    public long docLength(int docId) {
        return forwardIndex.docLength(field, docId);
    }

    /**
     * Get a Terms for a given field in this segment.
     *
     * The returned object is not thread-safe, so it should only
     * be used by a single thread. Subsequent calls will return
     * the same object.
     *
     * @return terms object for the given field
     */
    @Override
    public Terms terms() {
        if (terms == null) {
            // cache it, we don't need new instance every time
            terms = forwardIndex.terms(field);
        }
        return terms;
    }

    @Override
    public String getLuceneFieldName() {
        return field.getFieldName();
    }
}
