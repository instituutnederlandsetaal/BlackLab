package nl.inl.blacklab.search.textpattern;

import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.ExprType;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.extensions.XFSpans;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;
import nl.inl.blacklab.search.matchfilter.MatchFilter;
import nl.inl.blacklab.search.matchfilter.TextPatternStruct;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Describes some pattern of words in a content field. The point of this
 * interface is to provide an abstract layer to describe the pattern we're
 * interested in, which can then be translated into, for example, a SpanQuery
 * object or a String, depending on our needs.
 */
public abstract class TextPattern implements TextPatternStruct {

    /** Value meaning "no maximum" (actually just the largest integer) */
    public static final int MAX_UNLIMITED = BLSpanQuery.MAX_UNLIMITED;

    // Node types
    public static final String NT_AND = "and";
    public static final String NT_ANYTOKEN = "anytoken";
    public static final String NT_CAPTURE = "capture";
    public static final String NT_COMPARE = "compare";
    public static final String NT_CONSTRAINED = "constrained";
    public static final String NT_DEFVAL = "defval";
    public static final String NT_LOOK = "look";
    public static final String NT_EXPANSION = "expansion";
    public static final String NT_FILTERNGRAMS = "filterngrams";
    public static final String NT_FIXEDSPAN = "fixedspan";
    public static final String NT_FUZZY = "fuzzy";
    public static final String NT_IMPLICATION = "implication";
    public static final String NT_INT_RANGE = "intrange";
    public static final String NT_NOT = "not";
    public static final String NT_OR = "or";
    public static final String NT_POSFILTER = "posfilter";
    public static final String NT_OVERLAPPING = "overlapping";
    public static final String NT_PREFIX = "prefix";
    public static final String NT_CALL_FUNC = "callfunc";
    public static final String NT_REGEX = "regex";
    public static final String NT_RELATION_MATCH = "relmatch";
    public static final String NT_RELATION_TARGET = "reltarget";
    public static final String NT_REPEAT = "repeat";
    public static final String NT_SENSITIVITY = "sensitivity";
    public static final String NT_SEQUENCE = "sequence";
    public static final String NT_SETTINGS = "settings";
    public static final String NT_TAGS = "tags";
    public static final String NT_TERM = "term";
    public static final String NT_PROP_SELECT = "prop-selector";
    public static final String NT_VALUE_STRING = "string";
    public static final String NT_VALUE_BOOLEAN = "boolean";
    public static final String NT_VALUE_INTEGER = "integer";
    public static final String NT_VALUE_INT_RANGE = "int-range";
    public static final String NT_VALUE_SYMBOL = "symbol";
    public static final String NT_VALUE_UNDEFINED = "undefined";
    public static final String NT_WILDCARD = "wildcard";

    /**
     * Make sure the query is within the specified tag, and capture relations within the tag.
     *
     * E.g. you want hits inside sentences, and want to capture all (dependency) relations
     * in that sentence.
     *
     * Essentially adds <code>within rcapture(<s/>)</code> to the query if <code>tagNameRegex == "s"</code>.
     *
     * @param pattern pattern to filter
     * @param tagNameRegex tag the hits must be within
     * @return the filtered pattern, where relations within the tag will be captured
     */
    public static TextPatternPositionFilter createRelationCapturingWithinQuery(TextPattern pattern, String tagNameRegex, String captureRelsAs) {
        TextPattern tags = new TextPatternTags(tagNameRegex, null,
                TextPatternTags.Adjust.FULL_TAG, tagNameRegex);
        // Also capture any relations that are in the tag
        TextPatternValue tpCaptureRelsAs = TextPatternValue.fromObject(captureRelsAs);
        tags = new TextPatternFunctionCall(XFRelations.FUNC_RCAPTURE, List.of(tags, tpCaptureRelsAs));
        return new TextPatternPositionFilter(pattern, tags,
                SpanQueryPositionFilter.Operation.WITHIN);
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

    public interface EvalResult {
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

    @Override
    public abstract String toString();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    public BLSpanQuery toQuery(QueryInfo queryInfo) throws InvalidQuery {
        return toQuery(queryInfo, null, false, false);
    }

    public BLSpanQuery toQuery(QueryInfo queryInfo, Query filter, boolean adjustHits, boolean withSpans) throws InvalidQuery {
        TextPattern tp = this;
        if (adjustHits) {
            // Add rspan(..., 'all') so hit encompasses all matched relations
            tp = ensureHitSpansMatchedRelations(tp);
        }
        if (withSpans && !hasWithSpans()) {
            // Make sure we capture all overlapping spans
            tp = tp.applyWithSpans();
        }
        QueryExecutionContext context = queryInfo.index().defaultExecutionContext(queryInfo.field());
        EvalResult result = tp.evaluate(context);
        if (result == null)
            throw new InvalidQuery("Pattern evaluated to null");
        if (result instanceof BLSpanQuery spanQuery) {
            if (filter != null)
                spanQuery = new SpanQueryFiltered(spanQuery, filter);
            return spanQuery;
        }
        throw new InvalidQuery("Expected a query, but pattern evaluated to a " + ExprType.of(result));
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
}
