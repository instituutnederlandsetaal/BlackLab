package nl.inl.blacklab.search.indexmetadata;

import nl.inl.blacklab.search.BlackLabIndex;

/**
 * An annotation on a field with a specific sensitivity.
 * 
 * This defines a Lucene field in the BlackLab index.
 */
public interface AnnotationSensitivity {

    static AnnotationSensitivity fromFieldName(BlackLabIndex index, String fieldName) {
        String[] comp = AnnotatedFieldNameUtil.getNameComponents(fieldName);
        if (comp.length != 3)
            throw new IllegalArgumentException("Invalid field name: " + fieldName);
        return index.annotatedField(comp[0])
                .annotation(comp[1])
                .sensitivity(MatchSensitivity.fromLuceneFieldSuffix(comp[2]));
    }

    Annotation annotation();
	
	MatchSensitivity sensitivity();
	
	default String luceneField() {
		return AnnotatedFieldNameUtil.annotationSensitivity(annotation().luceneFieldPrefix(), sensitivity().luceneFieldSuffix());
	}
}
