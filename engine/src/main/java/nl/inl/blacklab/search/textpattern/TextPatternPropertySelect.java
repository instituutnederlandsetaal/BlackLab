package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;
import nl.inl.blacklab.search.matchfilter.MatchFilterSpan;
import nl.inl.blacklab.search.matchfilter.MatchFilterTokenAnnotation;

/**
 * Property selector . operator, used in constraints.
 *
 * Used to refer to a token annotation in constraints.
 *
 * E.g. A.lemma in query A:[] "after" B:[] :: A.lemma = B.lemma
 */
public class TextPatternPropertySelect extends TextPattern {

    public static int TP_PRECEDENCE = 2;

    private final TextPattern tpLabel;

    private final TextPattern tpAnnotation;

    /**
     * Reference to an annotation of a captured token in the query.
     *
     * @param label the capture group label
     * @param annotation the annotation name
     */
    public TextPatternPropertySelect(TextPattern label, TextPattern annotation) {
        super(TP_PRECEDENCE);
        this.tpLabel = label;
        this.tpAnnotation = annotation;
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery {
        // Evaluate left and right sides, getting the symbol, not MatchFilterSpan
        if (!context.isInConstraint())
            throw new InvalidQuery("Property selector . can only be used in constraints");
        // Select an annotation of a position
        // (e.g. A.lemma selects the lemma for the first token in capture group A)
        EvalResult resultLabel = tpLabel.evaluate(context);
        if (!(resultLabel instanceof MatchFilterSpan mfs))
            throw new InvalidQuery("Expected a reference to a span left of . for token annotation reference");
        if (tpAnnotation instanceof TextPatternValue tpv && tpv.value instanceof ConstraintValueSymbol cvsa) {
            return new MatchFilterTokenAnnotation(mfs.getGroupName(), cvsa.getValue());
        } else
            throw new InvalidQuery("Expected an annotation name right of . for token annotation reference");
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternPropertySelect that = (TextPatternPropertySelect) o;
        return Objects.equals(tpLabel, that.tpLabel) && Objects.equals(tpAnnotation, that.tpAnnotation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tpLabel, tpAnnotation);
    }

    @Override
    public String toString() {
        return "PROP_SEL(" + tpLabel + ", " + tpAnnotation + ")";
    }

    public TextPattern getLabel() {
        return tpLabel;
    }

    public TextPattern getAnnotation() {
        return tpAnnotation;
    }

    @Override
    public <T> T accept(TextPatternVisitor<T> visitor) {
        return visitor.visitPropertySelect(this);
    }
}
