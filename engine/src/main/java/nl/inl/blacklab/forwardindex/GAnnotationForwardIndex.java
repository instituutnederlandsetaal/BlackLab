package nl.inl.blacklab.forwardindex;

import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Global forward index for a single annotation on a field.
 */
@ThreadSafe
public interface GAnnotationForwardIndex {

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
