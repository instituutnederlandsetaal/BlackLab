package nl.inl.blacklab.search.indexmetadata;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorIntegrated;

/** An annotated field */
public interface AnnotatedField extends Field {

    /**
     * Get the annotations for this field.
     *
     * Properties are returned sorted according to the displayOrder defined in the
     * index metadata, if any.
     *
     * @return the annotations
     */
    Annotations annotations();

    /**
     * Main annotation, i.e. the words from the text.
     *
     * This is always the first annotation defined in the .blf.yaml config format file.
     * This is used for display, sorting and grouping (unless you explicitly request another annotation).
     * This is also the default for search, unless you explicitly specify another default search annotation.
     *
     * @return main annotation
     */
    default Annotation mainAnnotation() {
        return annotations().main();
    }

    /**
     * Default search annotation for BCQL queries.
     *
     * In BCQL, a double-quoted string without square brackets means a search on the default annotion.
     * This gives the annotation that will be searched. Defaults to the main annotation.
     *
     * @return default search anntotation
     */
    default Annotation defaultSearchAnnotation() {
        return annotations().defaultSearch();
    }

    default Annotation annotation(String name) {
        return annotations().get(name);
    }

    boolean hasRelationAnnotation();

    RelationsStats getRelationsStats(long limitValues);

    /**
     * Returns the Lucene field that contains the length (in tokens) of this field,
     * or null if there is no such field.
     *
     * @return the field name or null if lengths weren't stored
     */
    default String tokenLengthField() {
        return AnnotatedFieldNameUtil.lengthTokensField(name());
    }

    @Override
    default String contentsFieldName() {
        Annotation main = mainAnnotation();
        AnnotationSensitivity offsetsSensitivity = main.offsetsSensitivity();
        if (offsetsSensitivity == null)
            offsetsSensitivity = main.sensitivity(MatchSensitivity.SENSITIVE);
        return offsetsSensitivity.luceneField();
    }

    default ForwardIndexAccessor forwardIndexAccessor() {
        return new ForwardIndexAccessorIntegrated(index(), this);
    }

    default AnnotatedField withParallelFieldVersion(String version) {
        String name = AnnotatedFieldNameUtil.changeParallelFieldVersion(name(), version);
        return index().annotatedField(name);
    }
}
