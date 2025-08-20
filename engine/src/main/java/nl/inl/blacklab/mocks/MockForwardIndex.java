package nl.inl.blacklab.mocks;

import java.util.List;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * @param terms The unique terms in our index
 */
public record MockForwardIndex(Terms terms) implements AnnotationForwardIndex {

    @Override
    public String toString() {
        return "MockForwardIndex";
    }

    @Override
    public void initialize() {

    }

    @Override
    public Annotation annotation() {
        return null;
    }

    @Override
    public List<int[]> retrieveParts(int docId, int[] starts, int[] ends) {
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
