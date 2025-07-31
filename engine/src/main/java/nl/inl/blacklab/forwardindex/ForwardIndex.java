package nl.inl.blacklab.forwardindex;

import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Global forward index.
 *
 * A component that can quickly tell you what word occurs at a specific position
 * of a specific document.
 * 
 * This allows access to all annotations for a document, as opposed to {@link AnnotationForwardIndex},
 * which allows access to just one.
 */
@ThreadSafe
public interface ForwardIndex {

    /**
     * The field for which this is the forward index
     * 
     * @return field
     */
    AnnotatedField field();

    /**
     * Get a single-annotation view of the forward index.
     * 
     * @param annotation annotation to get a view of
     * @return single-annotation forward index view
     */
    AnnotationForwardIndex get(Annotation annotation);
}
