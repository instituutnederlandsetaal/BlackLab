package nl.inl.blacklab.search.indexmetadata;

import java.util.Iterator;
import java.util.stream.Stream;

/** Annotations on an annotated field. */
public interface Annotations extends Iterable<Annotation> {

    /**
     * The main annotation.
     * 
     * @return the main annotation
     */
    Annotation main();

    Annotation defaultSearch();

    /**
     * Iterate over the annotations.
     *
     * @return iterator
     */
    @Override
    Iterator<Annotation> iterator();

    /**
     * Stream the annotations.
     *
     * @return stream
     */
    Stream<Annotation> stream();
    
    /**
     * Does the specified annotation exist?
     * 
     * @param name annotation name
     * @return true if it exists, false if not
     */
    boolean exists(String name);

    /**
     * Get the description of one annotated field.
     * 
     * @param name name of the annotation
     * @return the annotation, or null if it doesn't exist
     */
    Annotation get(String name);

    /**
     * Get the punctuation annotation.
     * 
     * This contains the space between words, as well as any
     * punctuation. 
     * 
     * @return punctuation annotation
     */
    default Annotation punct() {
        return get(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME);
    }

    boolean isEmpty();

    /** Get the number of annotations.
     *
     * @return number of annotations
     */
    int size();
}
