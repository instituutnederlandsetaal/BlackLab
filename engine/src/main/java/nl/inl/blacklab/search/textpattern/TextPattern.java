package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.search.Query;
import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.ExprType;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.extensions.XFSpans;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;
import nl.inl.blacklab.search.matchfilter.MatchFilter;
import nl.inl.blacklab.search.matchfilter.MatchFilterCompare;
import nl.inl.blacklab.search.matchfilter.TextPatternStruct;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.util.StringUtil;

/**
 * Describes some pattern of words in a content field. The point of this
 * interface is to provide an abstract layer to describe the pattern we're
 * interested in, which can then be translated into, for example, a SpanQuery
 * object or a String, depending on our needs.
 */
public abstract class TextPattern implements TextPatternStruct {

    /** Value meaning "no maximum" (actually just the largest integer) */
    public static final int MAX_UNLIMITED = BLSpanQuery.MAX_UNLIMITED;

    public static TextPattern regex(String value, String annotation, MatchSensitivity sensitivity) {
        return new TextPatternRegex(value, annotation, sensitivity);
    }

    public static TextPattern term(String value, String annotation, MatchSensitivity sensitivity) {
        return new TextPatternTerm(value, annotation, sensitivity);
    }

    public static TextPattern regex(String value) {
        return regex(value, null, null);
    }

    public static TextPattern term(String value) {
        return term(value, null, null);
    }

    protected static TextPattern prefix(String prefix) {
        String regex = StringUtil.escapeLuceneRegexCharacters(prefix) + ".*";
        return regex(regex, null, null);
    }

    public static TextPattern wildcard(String wildcardExpr, String annotation, MatchSensitivity sensitivity) {
        String regex = StringUtil.escapeLuceneRegexCharacters(wildcardExpr)
                .replace("\\*", ".*")
                .replace("\\?", ".");
        return regex(regex, annotation, sensitivity);
    }

    public static TextPattern sequenceOfTerms(List<String> terms, Annotation annotation, MatchSensitivity sensitivity) {
        List<TextPattern> patterns = new ArrayList<>();
        String regexSensitivityPrefix = getRegexSensitivityPrefix(sensitivity, annotation.field().index().defaultMatchSensitivity());
        for (String term: terms) {
            TextPattern annot = new TextPatternValue(ConstraintValue.symbol(annotation.name()));
            TextPattern value = new TextPatternValue(ConstraintValue.get(regexSensitivityPrefix + StringUtil.escapeLuceneRegexCharacters(term)));
            TextPattern compare = new TextPatternCompare(annot, value, MatchFilterCompare.Operator.EQUAL);
            patterns.add(compare);
        }
        return patterns.size() == 1 ? patterns.get(0) : new TextPatternSequence(patterns);
    }

    private static @NonNull String getRegexSensitivityPrefix(MatchSensitivity sensitivity, MatchSensitivity defaultSensitivity) {
        String regexSensitivityPrefix = "";
        if (sensitivity != defaultSensitivity) {
            // We need a regex prefix to indicate that we want a different sensitivity than the default for the index.
            if (sensitivity == MatchSensitivity.INSENSITIVE)
                regexSensitivityPrefix = "(?i)";
            else if (sensitivity == MatchSensitivity.SENSITIVE)
                regexSensitivityPrefix = "(?-i)";
            else
                throw new UnsupportedOperationException("Unsupported sensitivity: " + sensitivity);
        }
        return regexSensitivityPrefix;
    }

    /**
     * Make sure the query is within the specified tag, and capture relations within the tag.
     *
     * E.g. you want hits inside sentences, and want to capture all (dependency) relations
     * in that sentence.
     *
     * Essentially adds <code>within rcapture(<s/>)</code> to the query if <code>tagNameRegex == "s"</code>.
     *
     * (actually, we use a special operation so this works even if the match spans multiple sentences,
     *  for the example of context=s)
     *
     * @param pattern pattern to filter
     * @param tagNameRegex tag the hits must be within
     * @return the filtered pattern, where relations within the tag will be captured
     */
    public static TextPattern createRelationCapturingWithinQuery(TextPattern pattern, String tagNameRegex, String captureRelsAs) {
        TextPattern filterUnit = new TextPatternTags(tagNameRegex, null,
                TextPatternTags.Adjust.FULL_TAG, tagNameRegex);
        return new TextPatternWithinTagContext(pattern, filterUnit, captureRelsAs);
    }

    /**
     * If the argument is a simple TextPatternTerm or TextPatternRegex, extract its string value.
     *
     * This is used to decide whether a function parameter value e.g. "duck" is a query [word="duck"] or
     * just a simple string value "duck", based on the declared type of the parameter.
     *
     * @param arg the argument
     * @return the string value, or null if not a simple term/regex
     */
    public static String getSimpleStringValue(QueryExecutionContext context, Object arg) {
        String strValue = null;
        if (arg instanceof TextPatternCompare tpc) {
            // E.g. passing a string to a function was interpreted as a query on the
            // default annotation. Extract the original string value.
            EvalResult result = tpc.getRightClause().evaluate(context);
            if (result instanceof ConstraintValue cv) {
                strValue = cv.asString().getValue();
            } else {
                // Not a simple string value
                return null;
            }
        } else if (arg instanceof TextPatternValue tpv) {
            // Retrieve string value
            ConstraintValue val = tpv.getValue();
            if (!(val instanceof ConstraintValueSymbol)) // causes problems with e.g. :: len(A) = 4
                strValue = val.asString().getValue();
        } else if (arg instanceof TextPatternAdditiveOp tadd) {
            // Try to evaluate expression directly (so e.g. --3 works)
            strValue = tadd.rewriteToConstant().toString();
        } else if (arg instanceof TextPatternRegex tpr) {
            // Interpret as regular string, not as a query
            // kind of a hack, but should work
            strValue = tpr.getValue();
            if (strValue.startsWith("^") && strValue.endsWith("$")) {
                // strip off ^ and $
                strValue = strValue.substring(1, strValue.length() - 1);
            }
        } else if (arg instanceof TextPatternTerm tpt) {
            // Interpret as regular string, not as a query
            strValue = tpt.getValue();
        }
        return strValue;
    }

    protected static String clausesToString(List<TextPattern> clauses) {
        StringBuilder b = new StringBuilder();
        for (TextPattern clause : clauses) {
            if (!b.isEmpty())
                b.append(", ");
            b.append(clause.toString());
        }
        return b.toString();
    }

    /** A result of evaluating a TextPattern, e.g. BLSpanQuery, MatchFilter, ConstraintValue, ... */
    public interface EvalResult {
    }

    TextPattern(int precedence) {
        this.precedence = precedence;
    }

    /**
     * Evaluate this TextPattern node.
     * This should be called when several result types are acceptable
     * (e.g. BLSpanQuery or a List (of BLSpanQueries)).
     *
     * @param context query execution context to use
     * @return resulting value
     * @throws InvalidQuery if something's wrong with the query (e.g. error in regex expression)
     */
    public abstract EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery;

    /**
     * Visit this TextPattern, calculating some result value.
     *
     * @param visitor the visitor to call, which will handle each TextPattern subclass
     */
    public abstract <T> T accept(TextPatternVisitor<T> visitor);

    /** Each subclass should set this in the constructor. */
    protected int precedence = Integer.MAX_VALUE;

    /** Get the precedence of this TextPattern node.
     *
     * Used to determine if parentheses are needed when serializing back to BCQL.
     */
    public int getPrecedence() {
        return precedence;
    }

    /**
     * Translate this TextPattern into a MatchFilter.
     *
     * This should be called when only a MatchFilter is acceptable as a result.
     *
     * @param context query execution context to use
     * @return resulting MatchFilter
     */
    public MatchFilter toMatchFilter(QueryExecutionContext context) throws InvalidQuery {
        EvalResult result = evaluate(context);
        if (result instanceof MatchFilter mf)
            return mf;
        throw new InvalidQuery("Expected a MatchFilter evaluating " + getClass().getName() + ", got a " + result.getClass().getName());
    }

    /**
     * Translate this TextPattern into a BLSpanQuery.
     *
     * This should be called when only a BLSpanQuery is acceptable as a result.
     *
     * @param context query execution context to use
     * @return resulting query
     * @throws InvalidQuery if something's wrong with the query (e.g. error in regex expression)
     */
    public BLSpanQuery toQuery(QueryExecutionContext context) throws InvalidQuery {
        EvalResult result = evaluate(context);
        if (result instanceof BLSpanQuery q)
            return q;
        throw new InvalidQuery("Expected a BLSpanQuery evaluating " + getClass().getName() + ", got a " + result.getClass().getName());
    }

    public BLSpanQuery toQuery(QueryInfo queryInfo) throws InvalidQuery {
        return toQuery(queryInfo, null);
    }

    public BLSpanQuery toQuery(QueryInfo queryInfo, Query filter) throws InvalidQuery {
        EvalResult result = evaluate(queryInfo.index().defaultExecutionContext(queryInfo.field(), queryInfo));
        if (result == null)
            throw new InvalidQuery("Pattern evaluated to null");
        if (result instanceof BLSpanQuery spanQuery) {
            if (filter != null)
                spanQuery = new SpanQueryFiltered(spanQuery, filter);
            return spanQuery;
        }
        throw new InvalidQuery("Expected a query, but pattern evaluated to a " + ExprType.of(result));
    }

    public TextPattern adjustTextPattern(boolean adjustHits, boolean withSpans) {
        TextPattern tp = this;
        if (adjustHits) {
            // Add rspan(..., 'all') so hit encompasses all matched relations
            tp = ensureHitSpansMatchedRelations(tp);
        }
        if (withSpans && !hasWithSpans()) {
            // Make sure we capture all overlapping spans
            tp = tp.applyWithSpans();
        }
        return tp;
    }

    /** Has with-spans() been applied to this query? (so we will capture all overlapping spans) */
    protected boolean hasWithSpans() {
        return false;
    }

    /** Apply with-spans to this TextPattern.
     * <p>
     * Either surrounds the whole query with a with-spans() call, or
     * applies it to the relation source and all relation targets (see {@link TextPatternRelationMatch}).
     */
    protected TextPattern applyWithSpans() {
        return new TextPatternFunctionCall(XFSpans.FUNC_WITH_SPANS, List.of(this));
    }

    /** Has rspan(.., 'all') been applied to this query? (so hits will be expanded to all matched relations) */
    protected boolean hasRspanAll() {
        return false;
    }

    /** Apply rspan(..., 'all') to this TextPattern.
     * <p>
     * Either surrounds the whole query with a with-spans() call, or
     * applies it to the relation source and all relation targets (see {@link TextPatternRelationMatch}).
     */
    protected TextPattern applyRspanAll() {
        TextPattern tpSpanType = TextPatternValue.fromObject("all");
        return new TextPatternFunctionCall(XFRelations.FUNC_RSPAN, List.of(this, tpSpanType));
    }

    /** Automatically add rspan so hit encompasses all matched relations.
     *
     * Only does this if this is a relations query and we don't already have
     * explicit calls to rspan or rel.
     */
    private static TextPattern ensureHitSpansMatchedRelations(TextPattern pattern) {
        if (pattern.isRelationsQuery() && !pattern.hasRspanAll())
            return pattern.applyRspanAll();
        return pattern;
    }

    /** Does this query involve any (e.g. dependency) relations operations?
     *
     * (we sometimes treat these queries slightly differently, e.g. automatically
     *  adjusting hits to encompass matched relations, if requested)
     */
    public boolean isRelationsQuery() {
        return false;
    }

    @Override
    public abstract String toString();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();
}
