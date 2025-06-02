package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * TextPattern for wrapping another TextPattern so that it applies to a certain
 * sensitivity.
 *
 * @deprecated shouldn't be necessary anymore, just pass sensitivity to TextPatternTerm or subclass directly
 */
@Deprecated
public class TextPatternSensitive extends TextPattern {

    private final TextPattern clause;

    private final MatchSensitivity sensitivity;

    /**
     * Indicate that we want to use a different list of alternatives for this part
     * of the query.
     * 
     * @param sensitivity search case-/diacritics-sensitively?
     * @param clause clause to apply to
     */
    public TextPatternSensitive(MatchSensitivity sensitivity, TextPattern clause) {
        this.sensitivity = sensitivity;
        this.clause = clause;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return clause.translate(context.withAnnotationAndSensitivity((Annotation) null, sensitivity));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternSensitive tp) {
            return sensitivity == tp.sensitivity &&
                    clause.equals(tp.clause);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return sensitivity.hashCode() + clause.hashCode();
    }

    @Override
    public String toString() {
        String sett = sensitivity.toString();
        return "SENSITIVE(" + sett + ", " + clause.toString() + ")";
    }
}
