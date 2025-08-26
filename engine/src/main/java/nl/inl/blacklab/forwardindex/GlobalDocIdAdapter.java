package nl.inl.blacklab.forwardindex;

import java.util.List;

/** Adapts a FieldForwardIndex to accept global doc ids.
 */
public class GlobalDocIdAdapter implements AnnotationForwardIndex {

    private final AnnotationForwardIndex forwardIndex;

    private final int docBase;

    public GlobalDocIdAdapter(AnnotationForwardIndex forwardIndex, int docBase) {
        this.forwardIndex = forwardIndex;
        this.docBase = docBase;
    }

    @Override
    public List<int[]> retrieveParts(int docId, int[] starts, int[] ends) {
        return forwardIndex.retrieveParts(docId - docBase, starts, ends);
    }

    @Override
    public int[] retrievePart(int docId, int start, int end) {
        return forwardIndex.retrievePart(docId - docBase, start, end);
    }

    @Override
    public long docLength(int docId) {
        return forwardIndex.docLength(docId - docBase);
    }

    @Override
    public Terms terms() {
        return forwardIndex.terms();
    }

    @Override
    public String getLuceneFieldName() {
        return forwardIndex.getLuceneFieldName();
    }
}
