package nl.inl.blacklab.search.matchfilter;

import java.util.List;
import java.util.Objects;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

/** Compare two constraint values and return an integer comparison result. */
public class MatchFilterCompare extends MatchFilter {

    public enum Operator {
        EQUAL("="),
        NOT_EQUAL("!="),
        LESS_THAN("<"),
        GREATER_THAN(">"),
        LESS_OR_EQUAL("<="),
        GREATER_OR_EQUAL(">=");

        public static Operator fromSymbol(String s) {
            for (Operator op: values()) {
                if (op.symbol.equals(s))
                    return op;
            }
            throw new IllegalArgumentException("Unknown operator: " + s);
        }

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public String toString() {
            return symbol;
        }

        public boolean perform(int cmp) {
            return switch (this) {
                case EQUAL -> cmp == 0;
                case NOT_EQUAL -> cmp != 0;
                case LESS_THAN -> cmp < 0;
                case GREATER_THAN -> cmp > 0;
                case LESS_OR_EQUAL -> cmp <= 0;
                case GREATER_OR_EQUAL -> cmp >= 0;
            };
        }
    }

    private final MatchFilter a;
    private final MatchFilter b;
    private final Operator operator;
    private final MatchSensitivity sensitivity;

    public MatchFilterCompare(MatchFilter a, MatchFilter b, Operator operator, MatchSensitivity sensitivity) {
        super();
        this.a = a;
        this.b = b;
        this.operator = operator;
        this.sensitivity = sensitivity;
    }

    @Override
    public String toString() {
        return a + " " + operator + " " + b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof MatchFilterCompare that))
            return false;
        return a.equals(that.a) && b.equals(that.b) && operator == that.operator && sensitivity == that.sensitivity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b, operator, sensitivity);
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        a.setHitQueryContext(context);
        b.setHitQueryContext(context);
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo) {
        ConstraintValue ra = a.evaluate(fiDoc, matchInfo);
        ConstraintValue rb = b.evaluate(fiDoc, matchInfo);

        // Compare values
        int cmp;
        if (ra instanceof ConstraintValueString && rb instanceof ConstraintValueString) {
            cmp = ((ConstraintValueString)ra).stringCompareTo((ConstraintValueString) rb, sensitivity);
        } else {
            cmp = ra.compareTo(rb);
        }
        // Return result of comparison depending on operator
        return ConstraintValue.get(operator.perform(cmp));
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        a.lookupAnnotationIndices(fiAccessor);
        b.lookupAnnotationIndices(fiAccessor);
    }

    @Override
    public MatchFilter withField(AnnotatedField field) {
        return twoClauseRewrite(this, a, b, (MatchFilter m) -> m.withField(field),
                (x, y) -> new MatchFilterCompare(x, y, operator, sensitivity));
    }

    @Override
    public MatchFilter forContext(LeafReaderContext context) {
        return twoClauseRewrite(this, a, b, (MatchFilter m) -> m.forContext(context),
                (x, y) -> new MatchFilterCompare(x, y, operator, sensitivity));
    }

    @Override
    public MatchFilter rewrite() {
        MatchFilter x = a.rewrite();
        MatchFilter y = b.rewrite();

        if (operator == Operator.EQUAL) {
            if (x instanceof MatchFilterTokenAnnotation && y instanceof MatchFilterString) {
                // Simple annotation to string comparison, e.g. a.word = "cow"
                // This can be done more efficiently without string comparisons
                String termString = ((MatchFilterString) y).getString();
                return ((MatchFilterTokenAnnotation) x).matchTokenString(termString, sensitivity);
            }

            if (x instanceof MatchFilterTokenAnnotation xtp && y instanceof MatchFilterTokenAnnotation ytp) {
                if (xtp.getAnnotationName().equals(ytp.getAnnotationName())) {
                    // Expression of the form a.word = b.word;
                    // This can be done more efficiently without string comparisons
                    return xtp.matchOtherTokenSameProperty(ytp.getGroupName(), sensitivity);
                }
            }
        }

        // Some other comparison.
        if (x != a || y != b) {
            // clauses rewritten; return new instance
            return new MatchFilterCompare(x, y, operator, sensitivity);
        }
        // return unchanged
        return this;
    }

    public Operator getOperator() {
        return operator;
    }

    public List<MatchFilter> getClauses() {
        return List.of(a, b);
    }

    public MatchSensitivity getSensitivity() {
        return sensitivity;
    }

}
