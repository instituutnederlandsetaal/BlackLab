package nl.inl.blacklab.search.indexmetadata;

import java.util.Objects;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorIntegrated;

/** An annotated field.
 *  <p>
 *  In addition to what Field knows (index, name, settings, etc.) it knows
 *  its annotations, including its main and search annotations, its token length
 *  field, etc.
 */
public interface AnnotatedField extends Field {

    /** Reference to an annotated field.
     * Used to investigate which parts of the code actually need access to the field object,
     * and which parts only need the name. Lower-level parts should ideally only need the name.
     * This improves testability. Same for access to the index object. */
    class Ref {
        private final AnnotatedField annotatedField;

        public Ref(BlackLabIndex index, String name) {
            this.annotatedField = index.annotatedField(name);
        }

        public Ref(AnnotatedField field) {
            this.annotatedField = field;
        }

        public String getName() {
            return annotatedField.name();
        }

        public AnnotatedField getAnnotatedField() {
            return annotatedField;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Ref ref))
                return false;
            return Objects.equals(annotatedField, ref.annotatedField);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(annotatedField);
        }

        @Override
        public String toString() {
            return annotatedField.toString();
        }

        /** Return a reference to another annotated field in the same index.
         *
         * (we hope to eventually replace Ref with just the field name, in which case this method will not be needed anymore)
         */
        public Ref otherAnnotatedField(String newTargetFieldName) {
            return new Ref(annotatedField.index().annotatedField(newTargetFieldName));
        }
    }

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
