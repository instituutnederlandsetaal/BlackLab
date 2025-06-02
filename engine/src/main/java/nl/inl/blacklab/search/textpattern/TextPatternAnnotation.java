package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * TextPattern for wrapping another TextPattern so that it applies to a certain
 * annotation on an annotated field.
 *
 * For example, to find lemmas starting with "bla": <code>
 * TextPattern tp = new TextPatternAnnotation("lemma", new TextPatternWildcard("bla*"));
 * </code>
 *
 * @deprecated shouldn't be necessary anymore, just pass annotation to TextPatternTerm or subclass directly
 */
@Deprecated
public class TextPatternAnnotation extends TextPattern {
    private final TextPattern clause;

    private final String annotationName;

    public TextPatternAnnotation(String annotationName, TextPattern clause) {
        this.annotationName = annotationName == null ? "" : annotationName;
        this.clause = clause;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        String[] parts = annotationName.split("/", -1);
        if (parts.length > 2)
            throw new InvalidQuery("Invalid query: annotation name '" + annotationName + "' contains more than one slash");
        String name = parts[0];
        if (parts.length > 1)
            name += AnnotatedFieldNameUtil.SUBANNOTATION_FIELD_PREFIX_SEPARATOR + parts[1];
        Annotation annotation = context.field().annotation(name);
        if (annotation == null)
            throw new InvalidQuery("Invalid query: annotation '" + name + "' doesn't exist");
        return clause.translate(context.withAnnotationAndSensitivity(annotation, null));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternAnnotation tp) {
            return clause.equals(tp.clause) && annotationName.equals(tp.annotationName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return clause.hashCode() + annotationName.hashCode();
    }

    @Override
    public String toString() {
        return "ANNOTATION(" + annotationName + ", " + clause.toString() + ")";
    }
}
