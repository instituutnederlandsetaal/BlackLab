package nl.inl.blacklab.tools.frequency.data;

import java.util.List;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;

/**
 * Info about an annotation we're grouping on.
 */
public final class AnnotationInfo {
    private final AnnotatedField annotatedField;
    private final List<Annotation> annotations;
    private final MatchSensitivity matchSensitivity = MatchSensitivity.INSENSITIVE;
    private final List<Terms> terms;

    public AnnotationInfo(final BlackLabIndex index, final BuilderConfig bCfg, final FreqListConfig fCfg) {
        this.annotatedField = index.annotatedField(bCfg.getAnnotatedField());
        this.annotations = fCfg.getAnnotations().stream().map(annotatedField::annotation).toList();
        this.terms = annotations.stream().map(index::annotationForwardIndex).map(AnnotationForwardIndex::terms)
                .toList();
    }

    public MatchSensitivity getMatchSensitivity() {
        return matchSensitivity;
    }

    public List<Terms> getTerms() {
        return terms;
    }

    public AnnotatedField getAnnotatedField() {
        return annotatedField;
    }

    public List<Annotation> getAnnotations() {
        return annotations;
    }

    public Terms getTermsFor(final Annotation annotation) {
        return terms.get(annotations.indexOf(annotation));
    }
}
