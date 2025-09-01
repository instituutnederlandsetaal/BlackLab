package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any annotation at any position from a
 * document.
 *
 * The two synchronized getAnnotationIndex methods are called when creating the
 * SpanWeight for a SpanQueryFiSeq. This could in theory be done at the same time
 * from different threads. After that the state of this class shouldn't change anymore,
 * so no more synchronisation is needed.
 */
@ThreadSafe
public abstract class ForwardIndexAccessorAbstract implements ForwardIndexAccessor {

    /** Our index */
    protected final BlackLabIndex index;

    /** Field name, e.g. "contents" */
    protected final AnnotatedField annotatedField;

    /** The annotations (indexed by annotation index) */
    protected final List<Annotation> annotations = new ArrayList<>();

    /** The annotation index for each annotation name (inverse of the list above) */
    private final Map<Annotation, Integer> annotationIndexes = new HashMap<>();

    /** The Lucene field that contains the forward index for each annotation */
    protected final List<String> luceneFields = new ArrayList<>();

    protected ForwardIndexAccessorAbstract(BlackLabIndex index, AnnotatedField searchField) {
        this.index = index;
        this.annotatedField = searchField;
    }

    /**
     * Get the index number corresponding to the given annotation name.
     *
     * @param annotation annotation to get the index for
     * @return index for this annotation
     */
    protected synchronized int getAnnotationIndex(Annotation annotation) {
        Integer n = annotationIndexes.get(annotation);
        if (n == null) {
            // Assign number and store reference to forward index
            n = annotationIndexes.size();
            annotationIndexes.put(annotation, n);
            annotations.add(annotation);
            luceneFields.add(annotation.forwardIndexSensitivity().luceneField());
        }
        return n;
    }

    @Override
    public int getAnnotationIndex(String annotationName) {
        return getAnnotationIndex(annotatedField.annotation(annotationName));
    }

    protected int numberOfAnnotations() {
        return annotations.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ForwardIndexAccessorAbstract that))
            return false;
        return index.equals(that.index) && annotatedField.equals(that.annotatedField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, annotatedField);
    }
}
