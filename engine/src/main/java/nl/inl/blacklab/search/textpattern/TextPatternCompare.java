package nl.inl.blacklab.search.textpattern;

import java.util.List;
import java.util.Objects;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.RegexpQuery;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryNot;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueIntRange;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;
import nl.inl.blacklab.search.matchfilter.MatchFilterCompare;
import nl.inl.util.RangeRegex;
import nl.inl.util.StringUtil;

/**
 * A TextPattern matching a word.
 */
public class TextPatternCompare extends TextPattern {

    /** Left operand, often annotation name */
    protected final TextPattern left;

    /** Right operand, e.g. value to match */
    protected final TextPattern right;

    /** Type of comparison, e.g. =, <=, etc. */
    protected final MatchFilterCompare.Operator operator;

    public TextPatternCompare(TextPattern left, TextPattern right, MatchFilterCompare.Operator operator) {
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    public static String regexForRange(int min, int max) {
        if (min > max)
            return RangeRegex.REGEX_WITHOUT_MATCHES;
        return RangeRegex.forRange(min, max);
    }

    @Override
    public Object evaluate(QueryExecutionContext context) throws InvalidQuery {
        TextPattern actualLeft = left instanceof TextPatternDefaultValue ? // use default annotation
                new TextPatternValue(ConstraintValue.symbol(context.field().mainAnnotation().name())) :
                left;
        if (context.isInConstraint()) {
            // Constraint.
            return new MatchFilterCompare(actualLeft.toMatchFilter(context),
                    right.toMatchFilter(context), operator, MatchSensitivity.INSENSITIVE);
        } else {
            // Regular query. Only equals supported.
            boolean isNot = operator == MatchFilterCompare.Operator.NOT_EQUAL;
            if (!isNot && operator != MatchFilterCompare.Operator.EQUAL)
                throw new InvalidQuery("Only equality comparisons are supported in queries, not " + operator);

            Object evaluated = actualLeft.evaluate(context); // TODO: pseudo-annotation punctBefore etc.
            if (evaluated instanceof ConstraintValueSymbol cvs) {
                evaluated = context.field().annotation(cvs.getValue());
            }
            BLSpanQuery query;
            if (evaluated instanceof Annotation annotation) {
                Object result2 = right.evaluate(context);
                String regex;
                if (result2 instanceof ConstraintValue cv) {
                    if (cv instanceof ConstraintValueIntRange cvir) {
                        regex = regexForRange(cvir.getMin(), cvir.getMax());
                    } else {
                        regex = cv.asString().getValue();
                    }
                } else {
                    throw new InvalidQuery("Right side of comparison must evaluate to a string or int range, not: "
                            + result2.getClass().getSimpleName());
                }

                // See if this is really a regex query or just a term query maskerading as one...
                TextPattern result = TextPatternRegex.rewriteToSimplerTextPattern(annotation.name(),
                        MatchSensitivity.INSENSITIVE, regex);
                if (result != null) {
                    // Rewritten into a TextPattern{Term|Regex}; translate that instead
                    query = result.toQuery(context);
                } else {
                    // We're dealing with an actual regex query.
                    context = context.withAnnotationAndSensitivity(annotation,
                            MatchSensitivity.INSENSITIVE);
                    String valueDesensitized = context.optDesensitize(regex);

                    // Lucene's regex engine requires double quotes to be escaped, unlike most others.
                    // Escape double quotes
                    valueDesensitized = StringUtil.escapeQuote(valueDesensitized, "\"");

                    try {
                        Term term = new Term(context.luceneField(), valueDesensitized);
                        RegexpQuery regexpQuery = new RegexpQuery(
                                term); //, RegExp.COMPLEMENT); causes issues with NFA matching!
                        query = new BLSpanMultiTermQueryWrapper<>(context.queryInfo(), regexpQuery);
                    } catch (IllegalArgumentException e) {
                        throw new InvalidQuery("Invalid query: " + e.getMessage() + " (while parsing regex)");
                    } catch (StackOverflowError e) {
                        // If we pass in a really large regular expression, like a huge
                        // list of words combined with OR, stack overflow occurs inside
                        // Lucene's automaton building code and we may end up here.
                        throw new RegexpTooLarge();
                    }
                }
            } else if (evaluated instanceof QueryFunction func) {
                // Pseudo-annotation, actually a function call
                query = new TextPatternFunctionCall(func.getName(), List.of(right)).toQuery(context);
            } else {
                throw new InvalidQuery("Left side of comparison must evaluate to an annotation or function, not: "
                        + evaluated.getClass().getSimpleName());
            }
            return isNot ? new SpanQueryNot(query) : query;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternCompare that = (TextPatternCompare) o;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right)
                && operator == that.operator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right, operator);
    }

    @Override
    public String toString() {
        return "CMP(" + left + ", " + operator + ", " + right + ")";
    }

    public TextPattern getLeftClause() {
        return left;
    }

    public TextPattern getRightClause() {
        return right;
    }

    public MatchFilterCompare.Operator getOperator() {
        return operator;
    }

    @Override
    public boolean isBracketQuery() {
        return left != TextPatternDefaultValue.get();
    }
}
