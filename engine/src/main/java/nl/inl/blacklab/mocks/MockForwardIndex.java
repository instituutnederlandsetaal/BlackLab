package nl.inl.blacklab.mocks;

import java.util.List;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * @param terms The unique terms in our index
 */
public record MockForwardIndex(Terms terms) implements AnnotationForwardIndex {

    @Override
    public ForwardIndex getParent() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void initialize() {
        // Nothing to do
    }

    @Override
    public List<int[]> retrievePartsInt(int globalDocId, int[] start, int[] end) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<int[]> retrievePartsIntSegment(LeafReaderContext lrc, int globalDocId, int[] start, int[] end) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int docLength(int docId) {
        return 0;
    }

    @Override
    public String toString() {
        return "MockForwardIndex";
    }

    @Override
    public Collators collators() {
        return null;
    }

    @Override
    public Annotation annotation() {
        return null;
    }

}
