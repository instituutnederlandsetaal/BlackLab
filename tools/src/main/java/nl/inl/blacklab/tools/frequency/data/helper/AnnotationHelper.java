package nl.inl.blacklab.tools.frequency.data.helper;

import java.util.List;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;

public record AnnotationHelper(
        AnnotatedField annotatedField,
        List<Annotation> annotations,
        List<AnnotationForwardIndex> forwardIndices
) {
    public static AnnotationHelper create(final BlackLabIndex index, final FrequencyListConfig cfg) {
        final var annotatedField = index.annotatedField(cfg.annotatedField());
        final var annotations = cfg.annotations().stream().map((var a) -> annotatedField.annotation(a.name())).toList();
        final var forwardIndices = annotations.stream().map(index::annotationForwardIndex).toList();
        return new AnnotationHelper(annotatedField, annotations, forwardIndices);
    }
}
