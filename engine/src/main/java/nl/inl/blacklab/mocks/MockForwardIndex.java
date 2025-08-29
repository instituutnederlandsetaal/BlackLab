package nl.inl.blacklab.mocks;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;

/**
 * @param terms The unique terms in our index
 */
public record MockForwardIndex(Terms terms) implements AnnotationForwardIndex {

    @Override
    public String toString() {
        return "MockForwardIndex";
    }

    @Override
    public int[][] retrieveParts(int docId, int[] starts, int[] ends) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int[] retrievePart(int docId, int start, int end) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long docLength(int docId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLuceneFieldName() {
        return "test";
    }
}
