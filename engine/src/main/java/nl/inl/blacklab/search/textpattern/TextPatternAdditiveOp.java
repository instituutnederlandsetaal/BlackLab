package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.matchfilter.ConstraintValueInt;
import nl.inl.blacklab.search.matchfilter.MatchFilter;
import nl.inl.blacklab.search.matchfilter.MatchFilterAdditiveOp;
import nl.inl.blacklab.search.matchfilter.MatchFilterValue;

/**
 * A TextPattern adding or subtracting two values.
 */
public class TextPatternAdditiveOp extends TextPattern {

    public static final String ERR_NO_ARITH_IN_QUERIES = "Arithmetic operations are not supported in queries, only in constraints.";
    public static int TP_PRECEDENCE = 4;

    /** Left operand */
    protected final TextPattern left;

    /** Right operand */
    protected final TextPattern right;

    /** Type of operation, e.g. +, - */
    protected final MatchFilterAdditiveOp.Operator operator;

    public TextPatternAdditiveOp(TextPattern left, TextPattern right, MatchFilterAdditiveOp.Operator operator) {
        super(TP_PRECEDENCE);
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery {
        if (context.isInConstraint()) {
            MatchFilter a = left == null ? null : left.toMatchFilter(context);
            MatchFilter b = right.toMatchFilter(context);
            if (left == null) {
                if (operator == MatchFilterAdditiveOp.Operator.MINUS) {
                    // Unary minus
                    if (b instanceof MatchFilterValue bv && bv.getValue() instanceof ConstraintValueInt bi)
                        return new MatchFilterValue(ConstraintValueInt.get(-bi.getValue()));
                    else
                        return new MatchFilterAdditiveOp(null, b, operator);
                } else {
                    // Unary plus
                    a = new MatchFilterValue(ConstraintValueInt.get(0));
                }
            }
            if (a instanceof MatchFilterValue av && av.getValue() instanceof ConstraintValueInt ai &&
                    b instanceof MatchFilterValue bv && bv.getValue() instanceof ConstraintValueInt bi) {
                // Constants; just add/subtract the numbers now.
                int result = operator.perform(ai.getValue(), bi.getValue());
                return new MatchFilterValue(ConstraintValueInt.get(result));
            }
            return new MatchFilterAdditiveOp(a, b, operator);
        } else {
            throw new InvalidQuery(ERR_NO_ARITH_IN_QUERIES);
        }
    }

    ConstraintValueInt rewriteToConstant() {
        if ((left == null || left instanceof TextPatternValue lv && lv.getValue() instanceof ConstraintValueInt) &&
                (right instanceof TextPatternValue rv && rv.getValue() instanceof ConstraintValueInt cvi)) {
            // Constants; just add/subtract the numbers now.
            int a = left == null ? 0 : ((ConstraintValueInt) ((TextPatternValue) left).getValue()).getValue();
            return ConstraintValueInt.get(operator.perform(a, cvi.getValue()));
        } else {
            throw new InvalidQuery(ERR_NO_ARITH_IN_QUERIES);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternAdditiveOp that = (TextPatternAdditiveOp) o;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right)
                && operator == that.operator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right, operator);
    }

    @Override
    public String toString() {
        return left == null ? operator.toString() + right : left + " " + operator + " " + right;
    }

    public TextPattern getLeftClause() {
        return left;
    }

    public TextPattern getRightClause() {
        return right;
    }

    public MatchFilterAdditiveOp.Operator getOperator() {
        return operator;
    }

    @Override
    public boolean isBracketQuery() {
        return left != TextPatternDefaultValue.get();
    }

    @Override
    public <T> T accept(TextPatternVisitor<T> visitor) {
        return visitor.visitAdditiveOp(this);
    }
}
