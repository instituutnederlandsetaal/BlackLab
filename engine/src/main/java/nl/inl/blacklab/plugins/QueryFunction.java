package nl.inl.blacklab.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryDefaultValue;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternTags;

/** A function that operates on (part of) a query and can be called from BCQL. */
public abstract class QueryFunction extends Plugin {

    /** Default value for a query parameter that means "any n-gram" (<code>[]*</code> ) */
    public static final String VALUE_QUERY_ANY_NGRAM = "_ANY_NGRAM_";

    /** Default value for a query parameter that means "any span" (<code><'.*' //></code>) */
    public static final String VALUE_ANY_SPAN = "_ANY_SPAN_";

    /** Function name */
    private final String name;

    /** Parameter types */
    private final List<ExprType> argTypes;

    /** Parameter default values, if any */
    private final List<Object> defaultValues;

    /** Is this a function that operates specifically on relations queries? */
    private final boolean relationsFunction;

    public QueryFunction(String name, List<ExprType> argTypes) {
        this(name, argTypes, null, false);
    }

    public QueryFunction(String name, List<ExprType> argTypes,
            List<Object> defaultValues) {
        this(name, argTypes, defaultValues, false);
    }

    public QueryFunction(String name, List<ExprType> argTypes,
            List<Object> defaultValues, boolean relationsFunction) {
        this.name = name;
        this.argTypes = argTypes;
        this.defaultValues = defaultValues == null ? Collections.emptyList() : defaultValues;
        this.relationsFunction = relationsFunction;
    }

    public List<Object> preprocessArgs(QueryExecutionContext context, List<TextPattern> args) {
        // Make sure argument are interpreted as the correct type
        // (the parser interprets all strings as queries, so we sometimes need to convert them back...)
        List<Object> newArgs = new ArrayList<>(args);
        for (int i = 0; i < args.size(); i++) {
            TextPattern arg = args.get(i);
            if (i >= argTypes.size())
                continue; // either vararg or too many param (will be caught later)
            ExprType type = getExpectedParameterType(i);
            Objects.requireNonNull(type);
            if (type == ExprType.ANY || type == ExprType.STRING || type == ExprType.INTEGER ||
                    type == ExprType.BOOLEAN) {
                String strValue = TextPattern.getSimpleStringValue(context, arg);
                if (strValue != null) {
                    switch (type) {
                    case INTEGER -> {
                        // Try to convert to number
                        try {
                            newArgs.set(i, Integer.parseInt(strValue));
                        } catch (NumberFormatException e) {
                            // Ignore, will be caught later as wrong type
                        }
                    }
                    case BOOLEAN -> {
                        // Convert to boolean
                        if (strValue.equalsIgnoreCase("true"))
                            newArgs.set(i, true);
                        else
                            newArgs.set(i, false);
                    }
                    default -> newArgs.set(i, strValue);
                    }
                }
            }
        }
        return newArgs;
    }

    public Object apply(QueryExecutionContext context, List<Object> args) {
        // Add any default argument values
        List<Object> newArgs = new ArrayList<>(args);
        int n = Math.max(newArgs.size(), requiredNumberOfArguments());
        for (int i = 0; i < n; i++) {
            ExprType expectedType = getExpectedParameterType(i);
            // Fill in default value for argument if missing
            if (i < defaultValues.size()) {
                Object defVal = getDefaultParameterValue(i);
                if (defVal == VALUE_QUERY_ANY_NGRAM) {
                    // Special case: any n-gram (usually meaning "don't care")
                    defVal = SpanQueryAnyToken.anyNGram(context.queryInfo(), context.luceneField());
                } else if (defVal == VALUE_ANY_SPAN) {
                    // Special case: any span (usually meaning "don't care")
                    defVal = context.index()
                            .tagQuery(context.queryInfo(), context.withRelationAnnotation().luceneFieldRef(),
                                    RelationUtil.ANY_TYPE_REGEX, null, TextPatternTags.Adjust.FULL_TAG, null);
                }
                if (i >= newArgs.size()) {
                    // Missing argument; use default value
                    if (defVal == null) {
                        // No default value available, error
                        throw new InvalidQuery(
                                "Missing argument " + (i + 1) + " for function " + getName()
                                        + " (no default value available)");
                    }
                    newArgs.add(defVal);
                } else if (newArgs.get(i) instanceof SpanQueryDefaultValue) {
                    // Explicitly set to undefined (_); use default value
                    newArgs.set(i, defVal);
                }
            }

            // See if last argument should be a list type, but was passed another value (with possibly additional
            // values after that). If this is the case, treat it as a vararg and wrap the remaining arguments in a list.
            if (i == argTypes.size() - 1 &&
                    (expectedType == ExprType.LIST || expectedType == ExprType.ANY || expectedType == ExprType.ANY_INCLUDING_QUERY) &&
                    !(newArgs.get(i) instanceof List) && newArgs.size() > argTypes.size()) {
                List<Object> varArgList = new ArrayList<>();
                for (int j = i; j < newArgs.size(); j++) {
                    varArgList.add(newArgs.get(j));
                }
                newArgs = newArgs.subList(0, i);
                newArgs.add(varArgList);
                break;
            }

            // Check argument type
            boolean wrongType = switch (expectedType) {
                case ANY_INCLUDING_QUERY -> false;
                case ANY -> newArgs.get(i) instanceof BLSpanQuery; // does not allow query
                case UNDEFINED -> newArgs.get(i) != null;
                case QUERY -> !(newArgs.get(i) instanceof BLSpanQuery);
                case STRING -> !(newArgs.get(i) instanceof String);
                case INTEGER -> !(newArgs.get(i) instanceof Integer);
                case INT_RANGE -> !(newArgs.get(i) instanceof Integer[] arr && arr.length == 2);
                case BOOLEAN -> !(newArgs.get(i) instanceof Boolean);
                case LIST -> !(newArgs.get(i) instanceof List);
                case SYMBOL -> throw new InvalidQuery("Function argument value cannot be of type SYMBOL");
                case MATCH_INFO -> !(newArgs.get(i) instanceof MatchInfo);
            };
            if (wrongType)
                throw new InvalidQuery(
                        "Argument " + (i + 1) + " for function " + getName() + " has the wrong type: expected "
                                + expectedType
                                + ", got " + ExprType.of(newArgs.get(i)));
        }

        if (newArgs.size() != argTypes.size())
            throw new InvalidQuery(
                    "Wrong number of arguments for query function " + getName() + ": expected " + argTypes.size()
                            + ", got " + newArgs.size());
        return applyFunc(context, newArgs);
    }

    protected abstract Object applyFunc(QueryExecutionContext context, List<Object> parameters);

    public int requiredNumberOfArguments() {
        return argTypes.size();
    }

    public Object getDefaultParameterValue(int i) {
        return defaultValues.get(i);
    }

    public ExprType getExpectedParameterType(int i) {
        if (i >= argTypes.size())
            return ExprType.ANY_INCLUDING_QUERY; // vararg of any type
        return argTypes.get(i);
    }

    public boolean isRelationsFunction() {
        return relationsFunction;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof QueryFunction that))
            return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return name + "(" + argTypes + ")";
    }
}
