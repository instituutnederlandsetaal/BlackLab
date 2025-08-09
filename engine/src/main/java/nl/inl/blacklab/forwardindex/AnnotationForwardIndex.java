package nl.inl.blacklab.forwardindex;

import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * A forward index for a single annotation on a field.
 */
@ThreadSafe
public interface AnnotationForwardIndex {

    /**
     * The annotation for which this is the forward index
     *
     * @return annotation
     */
    Annotation annotation();

    @Override
    String toString();

    Collators collators();

    int numberOfTerms();
}
