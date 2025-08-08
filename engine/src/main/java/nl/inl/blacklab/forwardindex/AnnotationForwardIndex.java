package nl.inl.blacklab.forwardindex;

import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * A forward index for a single annotation on a field.
 */
@ThreadSafe
public interface AnnotationForwardIndex {

    /**
     * Initialize this forward index (to be run in the background).
     */
    void initialize();

    /**
     * Get the Terms object in order to translate ids to token strings
     *
     * @return the Terms object
     */
    Terms terms();

    /**
     * The annotation for which this is the forward index
     *
     * @return annotation
     */
    Annotation annotation();

    @Override
    String toString();

    Collators collators();

    ForwardIndex getParent();
}
