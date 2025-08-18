package nl.inl.blacklab.mocks;

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
    public int numberOfTerms() {
        return terms.numberOfTerms();
    }
}
