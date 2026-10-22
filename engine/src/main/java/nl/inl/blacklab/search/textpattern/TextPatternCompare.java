package nl.inl.blacklab.search.textpattern;

import java.util.List;
import java.util.Objects;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.RegexpQuery;
import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.QueryExtensions;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.SpanQueryNot;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueIntRange;
import nl.inl.blacklab.search.matchfilter.ConstraintValueString;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;
import nl.inl.blacklab.search.matchfilter.MatchFilterCompare;
import nl.inl.util.RangeRegex;
import nl.inl.util.StringUtil;

/**
 * A TextPattern comparing two values.
 */
public class TextPatternCompare extends TextPattern {

    public static int TP_PRECEDENCE = 5;

    /** Left operand, often annotation name */
    private final TextPattern left;

    /** Right operand, e.g. value to match */
    private final TextPattern right;

    /** Type of comparison, e.g. =, <=, etc. */
    private final MatchFilterCompare.Operator operator;

    /** The sensitivity to use for comparison, or null to use the default sensitivity from the context */
    private final MatchSensitivity sensitivity;

    /** The left side of this comparison if it's a symbol (i.e. an annotation name); null otherwise */
    private final String leftSymbol;

    /** Is the left side the "default value" (i.e. the default annotation, i.e. "cow" means [word="cow"]) */
    private final boolean leftIsDefaultValue;

    /** The right side of this comparison if it's a string (i.e. a regex or just a simple term); null otherwise */
    private final RegexStripped rightString;

    public TextPatternCompare(TextPattern left, TextPattern right, MatchFilterCompare.Operator operator, MatchSensitivity sensitivity) {
        super(TP_PRECEDENCE);

        // If right side is a string, check if it's a regex with a sensitivity prefix and/or anchors, and strip those off.
        // Also remember if it's a simple term (not a regex) for later.
        if (right instanceof TextPatternValue value &&
            value.getValue() instanceof ConstraintValueString str) {
            // If the regex starts with a sensitivity prefix, e.g. (?s) for case-sensitive,
            // remember it and strip it from the value. This will allow cleaner optimizations.
            // (sensitivity prefix in the regex overrides the sensitivity parameter)
            // RegexStripped also strips off any anchors (^ and $) from the regex.
            String regex = str.getValue();
            RegexStripped stripped = new RegexStripped(regex, sensitivity);
            sensitivity = stripped.sensitivity();
            if (!stripped.regex().equals(regex)) {
                // We stripped off a sensitivity prefix and/or anchors; create a new right operand with the stripped regex
                right = new TextPatternValue(ConstraintValueString.get(stripped.regex()));
            }
            // Check and remember if right side is a string (i.e. a regex or just a simple term).
            rightString = stripped;
        } else {
            rightString = null;
        }

        // Check and remember if left side is a symbol (i.e. annotation name).
        leftIsDefaultValue = left instanceof TextPatternDefaultValue;
        if (left instanceof TextPatternValue tpv && tpv.getValue() instanceof ConstraintValueSymbol cvs) {
            leftSymbol = cvs.getValue();
        } else {
            leftSymbol = null;
        }

        this.left = left;
        this.right = right;
        this.operator = operator;
        this.sensitivity = sensitivity;
    }

    /** Get simple anytoken, term or regex query. */
    public static BLSpanQuery getSimpleMatchQuery(QueryExecutionContext context, String annotation, String value, MatchSensitivity sensitivity) {
        RegexStripped stripped = new RegexStripped(value, sensitivity);
        if (stripped.isAnyToken()) {
            // Any token query.
            return TextPatternAnyToken.ANY_SINGLE_TOKEN.toQuery(context);
        } else if (stripped.isLettersAndDigits()) {
            // Simple term query.
            TextPattern rewrittenPseudoAnnot = rewriteIfPseudoAnnotation(context, true, annotation, value);
            if (rewrittenPseudoAnnot != null)
                return rewrittenPseudoAnnot.toQuery(context);
            context = context.withAnnotationAndSensitivity(annotation, sensitivity);
            String value1 = context.getSensitivity().desensitize(value);
            return new BLSpanTermQuery(context.queryInfo(), new Term(context.luceneField(),
                    context.getSensitivity().desensitize(value1)));
        } else {
            // Regex query.
            if (stripped.regex().equals(value) && stripped.sensitivity().equals(sensitivity)) {
                return evalRegex(context, annotation, value, sensitivity);
            } else {
                return evalRegex(context, annotation, stripped.regex(), stripped.sensitivity());
            }
        }
    }

    private static @NonNull BLSpanMultiTermQueryWrapper<RegexpQuery> evalRegex(
            QueryExecutionContext context, String annotation, String value, MatchSensitivity sensitivity) throws InvalidQuery {

        // We're dealing with an actual regex query.
        context = context.withAnnotationAndSensitivity(annotation, sensitivity);
        String valueDesensitized = context.getSensitivity().desensitize(value);

        // Lucene's regex engine requires double quotes to be escaped, unlike most others.
        // Escape double quotes
        valueDesensitized = StringUtil.escapeQuoteForBcql(valueDesensitized, "\"");

        try {
            Term term = new Term(context.luceneField(), valueDesensitized);
            RegexpQuery regexpQuery = new RegexpQuery(term); //, RegExp.COMPLEMENT); causes issues with NFA matching!
            return new BLSpanMultiTermQueryWrapper<>(context.queryInfo(), regexpQuery);
        } catch (IllegalArgumentException e) {
            throw new InvalidQuery("Invalid query: " + e.getMessage() + " (while parsing regex)");
        } catch (StackOverflowError e) {
            // If we pass in a really large regular expression, like a huge
            // list of words combined with OR, stack overflow occurs inside
            // Lucene's automaton building code and we may end up here.
            throw new RegexpTooLarge();
        }
    }

    /**
     * If the annotation doesn't exist in the data, but there is a suitable
     * extension function, rewrite this term to a function call.
     *
     * @param context the query execution context
     */
    protected static TextPattern rewriteIfPseudoAnnotation(QueryExecutionContext context, boolean convertToRegex, String annotation, String value) {
        TextPattern rewrittenPseudoAnnot = null;
        if (!context.field().annotations().exists(annotation)) {
            // Annotation doesn't exist in the data.
            // Check if there's an extension function that functions as a pseudo-annotation,
            // e.g. punctAfter(".") to enable [punctAfter="."]
            if (QueryExtensions.exists(annotation)) {
                QueryFunction func = QueryExtensions.get(annotation);
                if (func.requiredNumberOfArguments() != 1)
                    throw new InvalidQuery("Pseudo-annotation function " + annotation + " must take exactly one argument.");
                String regex = convertToRegex ? StringUtil.escapeLuceneRegexCharacters(value) : value;
                TextPatternValue tpRegex = TextPatternValue.fromObject(regex);
                rewrittenPseudoAnnot = new TextPatternFunctionCall(annotation, List.of(tpRegex));
            }
        }
        return rewrittenPseudoAnnot;
    }

    public boolean isSimpleAnnotationValueComparison() {
        return (leftSymbol != null || leftIsDefaultValue) && rightString != null &&
                operator == MatchFilterCompare.Operator.EQUAL;
    }

    /** Is this a non-negated comparison with the default annotation?
     * <p>
     * (i.e. will this be serialized as "cat" instead of [word="cat"])?
     */
    public boolean isEqualsDefaultAnnotation() {
        TextPattern left = getLeftClause();
        if (left instanceof TextPatternDefaultValue) {
            // Special case: a top-level string in BCQL is comparing with the default annotation
            // (i.e. "cow" means [word="cow"], assuming word is the default annotation)
            if (operator == MatchFilterCompare.Operator.EQUAL && getRightClause() instanceof TextPatternValue tpv &&
                    tpv.getValue() instanceof ConstraintValueString) {
                return true;
            }
        }
        return false;
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery {
        TextPattern actualLeft = left instanceof TextPatternDefaultValue ? // use default annotation
                new TextPatternValue(ConstraintValue.symbol(context.field().defaultSearchAnnotation().name())) :
                left;
        MatchSensitivity sensitivity = this.sensitivity == null ? context.getSensitivity() : this.sensitivity;
        if (context.isInConstraint()) {
            // Constraint.
            return new MatchFilterCompare(actualLeft.toMatchFilter(context),
                    right.toMatchFilter(context), operator, sensitivity);
        } else {
            // Regular query. Only [not] equals supported.
            boolean isNot = operator == MatchFilterCompare.Operator.NOT_EQUAL;
            if (!isNot && operator != MatchFilterCompare.Operator.EQUAL)
                throw new InvalidQuery("Only equality comparisons are supported in queries, not " + operator);

            EvalResult evaluated = actualLeft.evaluate(context);
            if (evaluated instanceof ConstraintValueSymbol cvs) {
                evaluated = context.field().annotation(cvs.getValue());
            }
            BLSpanQuery query;
            if (evaluated instanceof Annotation annotation) {
                EvalResult result2 = right.evaluate(context);
                String regex;
                if (result2 instanceof ConstraintValue cv) {
                    if (cv instanceof ConstraintValueIntRange cvir) {
                        regex = RangeRegex.forRange(cvir.getMin(), cvir.getMax());
                    } else {
                        regex = cv.asString().getValue();
                    }
                } else {
                    throw new InvalidQuery("Right side of comparison must evaluate to a string or int range, not: "
                            + result2.getClass().getSimpleName());
                }

                // Rewrite to appropriate query, i.e. anytoken, term or regex.
                query = getSimpleMatchQuery(context, annotation.name(), regex, sensitivity);
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
        if (!(o instanceof TextPatternCompare that))
            return false;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right)
                && operator == that.operator && sensitivity == that.sensitivity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right, operator, sensitivity);
    }

    @Override
    public String toString() {
        String optForceSens = sensitivity == null ? "" : ", " + sensitivity;
        if (leftIsDefaultValue && rightString != null) {
            // Special case: to avoid having to update all the tests,
            // serialize simple term comparisons like this (like old TextPatternTerm).
            return "TERM(" + rightString.regex() + optForceSens + ")";
        }
        return "CMP(" + left + ", " + operator + ", " + right + optForceSens + ")";
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

    public MatchSensitivity getSensitivity() {
        return sensitivity;
    }

    @Override
    public boolean isBracketQuery() {
        return left != TextPatternDefaultValue.get();
    }

    @Override
    public <T> T accept(TextPatternVisitor<T> visitor) {
        return visitor.visitCompare(this);
    }

    public RegexStripped getRightString() {
        return rightString;
    }

    public String getLeftSymbol() {
        return leftSymbol;
    }
}
