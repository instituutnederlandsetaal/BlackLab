package nl.inl.blacklab.forwardindex;

import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Global forward index for a single annotation on a field.
 */
@ThreadSafe
public interface AnnotationForwardIndex extends AnnotForwardIndex {

    /**
     * The annotation for which this is the forward index
     *
     * @return annotation
     */
    Annotation annotation();

    @Override
    String toString();

    void initialize();

}
