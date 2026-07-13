package nl.inl.blacklab.search.matchfilter;

import java.util.List;
import java.util.Objects;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.plugins.ExprType;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

/** Compare two constraint values and return an integer comparison result. */
public class MatchFilterAdditiveOp extends MatchFilter {

    public enum Operator {
        PLUS("+"),
        MINUS("-");

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

        public int perform(int a, int b) {
            return switch (this) {
                case PLUS -> a + b;
                case MINUS -> a - b;
            };
        }
    }

    private final MatchFilter a;
    private final MatchFilter b;
    private final Operator operator;

    public MatchFilterAdditiveOp(MatchFilter a, MatchFilter b, Operator operator) {
        super();
        this.a = a;
        this.b = b;
        this.operator = operator;
    }

    @Override
    public String toString() {
        return a == null ? operator.toString() + b : a + " " + operator + " " + b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof MatchFilterAdditiveOp that))
            return false;
        return Objects.equals(a, that.a) && Objects.equals(b, that.b) && operator == that.operator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b, operator);
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        if (a != null)
            a.setHitQueryContext(context);
        b.setHitQueryContext(context);
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo) {
        ConstraintValue ra = a == null ? null : a.evaluate(fiDoc, matchInfo);
        ConstraintValue rb = b.evaluate(fiDoc, matchInfo);

        int b = ((ConstraintValueInt)ConstraintValue.convertToType(rb, ExprType.INTEGER)).getValue();
        if (ra == null) {
            // Unary plus/minus
            return ConstraintValueInt.get(operator == Operator.MINUS ? -b : b);
        }

        // Return result of comparison depending on operator
        int a = ((ConstraintValueInt)ConstraintValue.convertToType(ra, ExprType.INTEGER)).getValue();
        return ConstraintValue.get(operator.perform(a, b));
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        a.lookupAnnotationIndices(fiAccessor);
        b.lookupAnnotationIndices(fiAccessor);
    }

    @Override
    public MatchFilter withField(AnnotatedField field) {
        return twoClauseRewrite(this, a, b, (MatchFilter m) -> m.withField(field),
                (x, y) -> new MatchFilterAdditiveOp(x, y, operator));
    }

    @Override
    public MatchFilter forSegment(LeafReaderContext context) {
        return twoClauseRewrite(this, a, b, (MatchFilter m) -> m.forSegment(context),
                (x, y) -> new MatchFilterAdditiveOp(x, y, operator));
    }

    @Override
    public MatchFilter rewrite() {
        MatchFilter x = a.rewrite();
        MatchFilter y = b.rewrite();

        // Some other comparison.
        if (x != a || y != b) {
            // clauses rewritten; return new instance
            return new MatchFilterAdditiveOp(x, y, operator);
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

}
