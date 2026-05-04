package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.QueryExtensions;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.extensions.XFSpans;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.MatchFilter;
import nl.inl.blacklab.search.matchfilter.MatchFilterFunctionCall;
import nl.inl.blacklab.search.matchfilter.MatchFilterValue;

/**
 * A TextPattern that applies a function to a list of patterns to produce a new pattern.
 *
 * Right now, this is used for testing purposes, e.g. to experiment with certain optimizations,
 * specifically forward index matching.
 */
public class TextPatternFunctionCall extends TextPattern {

    public static int TP_PRECEDENCE = 2;

    private final QueryFunction func;

    private final List<TextPattern> args;

    public TextPatternFunctionCall(String funcName, List<TextPattern> args) {
        super(TP_PRECEDENCE);
        if (!QueryExtensions.exists(funcName))
            throw new InvalidQuery("Function not found: " + funcName);
        this.func = QueryExtensions.get(funcName);
        this.args = args == null ? List.of() : new ArrayList<>(args);
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) throws InvalidQuery {

        // Make sure arguments are interpreted with the correct types
        // (e.g. "duck" can be a string or a query)
        List<Object> preprocessedArgs = func.preprocessArgs(context, args);

        // Evaluate to e.g. BLSpanQuery or MatchFilter
        List<Object> translated = evaluateArgs(context, preprocessedArgs);

        if (context.isInConstraint()) {
            List<MatchFilter> matchFilters = translated.stream().map(this::toMatchFilter).toList();
            return new MatchFilterFunctionCall(func, matchFilters);
        } else {
            List<Object> unpacked = getConstraintValues(translated);
            return func.apply(context, unpacked);
        }
    }

    private MatchFilter toMatchFilter(Object t) {
        if (t instanceof MatchFilter mf) {
            return mf;
        } else if (t instanceof ConstraintValue cv) {
            return new MatchFilterValue(cv);
        } else if (t == null || t instanceof String || t instanceof Integer || t instanceof Boolean || t instanceof Integer[] ||
                t instanceof MatchInfo || t instanceof List) {
            return new MatchFilterValue(ConstraintValue.fromObject(t));
        } else {
            throw new InvalidQuery("Cannot convert value of type " + t.getClass().getSimpleName() + " to MatchFilter");
        }
    }

    private List<Object> getConstraintValues(List<Object> translated) {
        List<Object> result = new ArrayList<>();
        for (Object o: translated) {
            if (o instanceof ConstraintValue cv) {
                result.add(cv.getValue());
            } else {
                result.add(o);
            }
        }
        return result;
    }

    private List<Object> evaluateArgs(QueryExecutionContext context, List<?> args) {
        List<Object> evaluated = new ArrayList<>();
        for (Object arg: args) {
            if (arg instanceof TextPattern) {
                // Evaluate any TextPattern arguments to get their actual value
                // (may be BLSpanQuery, MatchFilter, List, String, etc.)
                evaluated.add(((TextPattern) arg).evaluate(context));
            } else if (arg instanceof List) {
                // Recursively translate lists of arguments
                evaluated.add(evaluateArgs(context, (List<?>)arg));
            } else {
                // Just copy other argument types
                evaluated.add(arg);
            }
        }
        return evaluated;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((args == null) ? 0 : args.hashCode());
        result = prime * result + ((func == null) ? 0 : func.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TextPatternFunctionCall other = (TextPatternFunctionCall) obj;
        if (args == null) {
            if (other.args != null)
                return false;
        } else if (!args.equals(other.args))
            return false;
        if (func == null) {
            if (other.func != null)
                return false;
        } else if (!func.equals(other.func))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "QFUNC(" + getFunctionName() + ", " + StringUtils.join(args, ", ") + ")";
    }

    public String getFunctionName() {
        return func.getName();
    }

    public List<TextPattern> getArgs() {
        return args;
    }

    @Override
    public boolean isRelationsQuery() {
        if (func.isRelationsFunction())
            return true;
        for (TextPattern arg : args) {
            if (arg.isRelationsQuery())
                return true;
        }
        return false;
    }

    /** Is this a call to with-spans()? */
    @Override
    protected boolean hasWithSpans() {
        String n = getFunctionName();
        if (n.charAt(0) == '_') {
            // TODO: no longer needed? (we used to use _with-spans() from the frontend
            //     to strip it out again later, but we now use the JSON structure?)
            n = n.substring(1); // remove leading underscore
        }
        return n.equals(XFSpans.FUNC_WITH_SPANS);
    }

    /** Is this a call to rspan(..., 'all')? */
    @Override
    protected boolean hasRspanAll() {
        // See if we're already doing explicit rspan or rel call (if so, don't add rspan(..., 'all'),
        // even if adjusthits=true)
        return getFunctionName().equals(XFRelations.FUNC_RSPAN) || getFunctionName().equals(XFRelations.FUNC_REL);
    }

    @Override
    public <T> T accept(TextPatternVisitor<T> visitor) {
        return visitor.visitFunctionCall(this);
    }
}
