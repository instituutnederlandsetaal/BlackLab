package nl.inl.blacklab.search.indexmetadata;

import java.util.Collection;
import java.util.Set;

import nl.inl.blacklab.index.annotated.AnnotationSensitivities;

/** An annotation on an annotated field. */
public interface Annotation {
    
	/** @return field for which this is an annotation */
	AnnotatedField field();

	/** @return this annotation's name */
	String name();

    /**
     * What type of UI should be shown for this annotation?
     * 
     * Note that BlackLab doesn't do anything with this value; it is merely a convenience for the frontend.
     * 
     * @return desired UI type
     */
    String uiType();

    String description();

	boolean hasForwardIndex();

	/**
	 * Return which sensitivity-indexing contains character offset information.
	 *
	 * Note that there may not be such a sensitivity-indexing.
	 *
	 * @return the alternative, or null if there is none.
	 */
	AnnotationSensitivity offsetsSensitivity();

	/**
	 * What sensitivity alternatives were indexed for this annotation?
	 * @return the sensitivity setting
	 */
	Collection<AnnotationSensitivity> sensitivities();

	/**
	 * Was this annotation indexed with the sensitivity specified?
	 * @param sensitivity desired sensitivity
	 * @return true if yes, false if not
	 */
	boolean hasSensitivity(MatchSensitivity sensitivity);
	
	/**
	 * Get a reference to the specified sensitivity.
	 * 
	 * @param sensitivity desired sensitivity
	 * @return reference to the specified sensitivity.
	 */
	AnnotationSensitivity sensitivity(MatchSensitivity sensitivity);

    String displayName();

    /**
     * Is this 'annotation' internal to BlackLab?
     * 
     * Some 'annotations' are not really annotations from the input files,
     * but internal information for BlackLab. These shouldn't be shown in the
     * user interface.
     *  
     * @return true if the annotation is internal
     */
    boolean isInternal();

    /**
     * Return the Lucene field prefix for this annotation.
     * @return Lucene field prefix
     */
	default String luceneFieldPrefix() {
		return AnnotatedFieldNameUtil.annotationField(field().name(), name());
	}

    default String forwardIndexIdField() {
        return AnnotatedFieldNameUtil.forwardIndexIdField(luceneFieldPrefix());
    }

    /**
     * Get names of the subannotations for this annotation.
     * 
     * @return names of annotations that are considered subannotations of this annotation
     */
    Set<String> subannotationNames();
    
    @Override
    boolean equals(Object obj);
    
    @Override
    int hashCode();

    void setSubAnnotation(Annotation parentAnnotation);

	/**
	 * Get the alternative that has the forward index.
	 *
	 * @return alternative with the forward index
	 * @throws RuntimeException if annotation has no forward index
	 */
    default AnnotationSensitivity forwardIndexSensitivity() {
		if (!hasForwardIndex())
			throw new UnsupportedOperationException("Annotation has no forward index: " + name());
        return mainSensitivity();
	}

    /**
     * Get the main sensitivity.
     *
     * If the field has a forward index, content store and/or offsets,
     * they will be stored in this sensitivity.
     *
     * @return main sensitivity
     * @throws RuntimeException if annotation has no forward index
     */
    default AnnotationSensitivity mainSensitivity() {
        if (hasSensitivity(MatchSensitivity.SENSITIVE)) {
            return sensitivity(MatchSensitivity.SENSITIVE);
        } else
            return sensitivity(MatchSensitivity.INSENSITIVE);
    }

    /**
     * Get the sensitivity setting that corresponds to the available sensitivities.
     *
     * The sensitivity setting is what is specified in the input format config,
     * and is stored in the (integrated) index metadata.
     *
     * @return sensitivity setting
     */
    default AnnotationSensitivities sensitivitySetting() {
        boolean s = hasSensitivity(MatchSensitivity.SENSITIVE);
        boolean i = hasSensitivity(MatchSensitivity.INSENSITIVE);
        boolean ci = hasSensitivity(MatchSensitivity.CASE_INSENSITIVE);
        boolean di = hasSensitivity(MatchSensitivity.DIACRITICS_INSENSITIVE);

        if (s && i && ci && di)
            return AnnotationSensitivities.CASE_AND_DIACRITICS_SEPARATE;
        else if (s && i)
            return AnnotationSensitivities.SENSITIVE_AND_INSENSITIVE;
        else if (i)
            return AnnotationSensitivities.ONLY_INSENSITIVE;
        else if (s)
            return AnnotationSensitivities.ONLY_SENSITIVE;
        else
            return null; //throw new IllegalStateException("No sensitivities for annotation " + name());
    }

    CustomProps custom();

    boolean isSubannotation();

    Annotation parentAnnotation();

    default boolean isRelationAnnotation() {
        return AnnotatedFieldNameUtil.isRelationAnnotation(name());
    }

    /** We include the field name when serializing. This is the separator we use.
     *  Field or annotation names cannot include this separator.
     */
    String FIELD_ANNOTATION_SEPARATOR = AnnotatedFieldNameUtil.ANNOT_SEP;

    /** A combined field name and annotation name, used for e.g. serializing HitProperty and PropertyValue. */
    default String fieldAndAnnotationName() {
        return field().name() + FIELD_ANNOTATION_SEPARATOR + name();
    }
}
