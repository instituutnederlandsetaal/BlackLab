package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.QueryExtensions;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;
import nl.inl.blacklab.search.matchfilter.MatchFilterSpan;
import nl.inl.blacklab.search.matchfilter.MatchFilterValue;

/**
 * An immediate value: string, int, boolean, etc.
 */
public class TextPatternValue extends TextPattern {

    public static int TP_PRECEDENCE = 0;

    /*
     * The maximum number of tokens in this stretch.
     */
    protected final ConstraintValue value;

    public static TextPatternValue fromObject(Object o) {
        return new TextPatternValue(ConstraintValue.fromObject(o));
    }

    public TextPatternValue(ConstraintValue value) {
        super(TP_PRECEDENCE);
        this.value = value;
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) {
        if (context.isInConstraint()) {
            if (value instanceof ConstraintValueSymbol cvs) {
                /** In constraint, symbol A in  :: start(A)  means "pass the span captured for A to start()",
                 *     but symbol lemma in  :: A.lemma = "koe"  means "fetch annotation lemma".
                 *  Note also that e.g. :: !A  means "span for A is undefined".
                 *  context.symbolRefersToSpan() indicates where we are in the query, so that we know how to
                 *  interpret a symbol. */
                return new MatchFilterSpan(cvs.getValue());
            }
            return new MatchFilterValue(value);
        } else {
            if (value instanceof ConstraintValueSymbol cvs) {
                // Look up the annotation the symbol represents
                String name = cvs.getValue();
                Annotation annotation = context.field().annotation(name);
                if (annotation == null) {
                    // Annotation doesn't exist in the data.
                    // Check if there's an extension function that functions as a pseudo-annotation,
                    // e.g. punctAfter(".") to enable [punctAfter="."]
                    if (QueryExtensions.exists(name)) {
                        QueryFunction queryFunction = QueryExtensions.get(name);
                        if (queryFunction.requiredNumberOfArguments() != 1)
                            throw new InvalidQuery("Extension function " + name + " cannot be used as a pseudo-annotation because it requires " + queryFunction.requiredNumberOfArguments() + " arguments (expected 1)");
                        return queryFunction;
                    }
                }
                if (annotation == null) {
                    throw new InvalidQuery("Annotation not found: " + name);
                }
                return annotation;
            }
            return value;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternValue tp) {
            return value.equals(tp.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }

    public ConstraintValue getValue() {
        return value;
    }
}
