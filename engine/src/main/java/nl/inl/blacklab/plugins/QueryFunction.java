package nl.inl.blacklab.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryDefaultValue;
import nl.inl.blacklab.search.textpattern.TextPatternRegex;
import nl.inl.blacklab.search.textpattern.TextPatternTags;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;

/** A function that operates on (part of) a query and can be called from BCQL. */
public abstract class QueryFunction extends Plugin {

    /** Default value for a query parameter that means "any n-gram" (<code>[]*</code> ) */
    public static final String VALUE_QUERY_ANY_NGRAM = "_ANY_NGRAM_";

    /** Default value for a query parameter that means "any span" (<code><'.*' //></code>) */
    public static final String VALUE_ANY_SPAN = "_ANY_SPAN_";

    /** Value to pass if there are no default parameter values. */
    public static List<Object> NO_DEFAULT_VALUES = Collections.emptyList();

    /** Variable number of query params */
    public static List<ArgType> ARGS_VAR_Q = List.of(ArgType.QUERY, ArgType.ELLIPSIS);

    /** Variable number of string params */
    public static List<ArgType> ARGS_VAR_S = List.of(ArgType.STRING, ArgType.ELLIPSIS);

    /** Two strings */
    public static List<ArgType> ARGS_S = List.of(ArgType.STRING);

    /** A single query as an argument */
    public static List<ArgType> ARGS_Q = List.of(ArgType.QUERY);

    /** Two strings */
    public static List<ArgType> ARGS_SS = List.of(ArgType.STRING, ArgType.STRING);

    /** Two strings */
    public static List<ArgType> ARGS_SQ = List.of(ArgType.STRING, ArgType.QUERY);

    /** A query and a string */
    public static List<ArgType> ARGS_QS = List.of(ArgType.QUERY, ArgType.STRING);

    /** Two queries as an argument */
    public static List<ArgType> ARGS_QQ = List.of(ArgType.QUERY, ArgType.QUERY);

    /** Two strings */
    public static List<ArgType> ARGS_SSS = List.of(ArgType.STRING, ArgType.STRING, ArgType.STRING);

    /** A query, a string and another query */
    public static List<ArgType> ARGS_SSQ = List.of(ArgType.STRING, ArgType.STRING, ArgType.QUERY);

    /** A query, a string and another query */
    public static List<ArgType> ARGS_SQS = List.of(ArgType.STRING, ArgType.QUERY, ArgType.STRING);

    /** A query, a string and another query */
    public static List<ArgType> ARGS_SQQ = List.of(ArgType.STRING, ArgType.QUERY, ArgType.QUERY);

    /** A query, a string and another query */
    public static List<ArgType> ARGS_QSS = List.of(ArgType.QUERY, ArgType.STRING, ArgType.STRING);

    /** A query, a string and another query */
    public static List<ArgType> ARGS_QSQ = List.of(ArgType.QUERY, ArgType.STRING, ArgType.QUERY);

    /** A query, a string and another query */
    public static List<ArgType> ARGS_QQS = List.of(ArgType.QUERY, ArgType.QUERY, ArgType.STRING);

    /** Three queries as an argument */
    public static List<ArgType> ARGS_QQQ = List.of(ArgType.QUERY, ArgType.QUERY, ArgType.QUERY);

    /** Two queries, two strings */
    public static List<ArgType> ARGS_QQSS = List.of(ArgType.QUERY, ArgType.QUERY, ArgType.STRING, ArgType.STRING);

    /** A string, a query, and three strings */
    public static List<ArgType> ARGS_SQSS = List.of(ArgType.STRING, ArgType.QUERY, ArgType.STRING, ArgType.STRING);

    /** A string, a query, and three strings */
    public static List<ArgType> ARGS_SQSSS = List.of(ArgType.STRING, ArgType.QUERY, ArgType.STRING, ArgType.STRING, ArgType.STRING);

    /** A query and three strings */
    public static List<ArgType> ARGS_QSSS = List.of(ArgType.QUERY, ArgType.STRING, ArgType.STRING, ArgType.STRING);


    /** Function name */
    private final String name;

    /** Parameter types */
    private final List<ArgType> argTypes;

    /** Parameter default values, if any */
    private final List<Object> defaultValues;

    /** Does this take a variable number of arguments? */
    private final boolean isVarArg;

    /** Is this a function that operates specifically on relations queries? */
    private final boolean relationsFunction;

    public QueryFunction(String name,List<ArgType> argTypes,
            List<Object> defaultValues, boolean relationsFunction) {
        this.name = name;
        this.argTypes = argTypes;
        isVarArg = argTypes.size() == 2 && argTypes.get(1) == ArgType.ELLIPSIS;
        if (isVarArg) {
            if (argTypes.get(0) == ArgType.ELLIPSIS)
                throw new IllegalArgumentException("Illegal var args type ELLIPSIS");
        } else {
            if (argTypes.stream().anyMatch(t -> t == ArgType.ELLIPSIS))
                throw new IllegalArgumentException("Illegal argument type ELLIPSIS");
        }
        this.defaultValues = defaultValues == null ? Collections.emptyList() : defaultValues;
        this.relationsFunction = relationsFunction;
    }

    public List<Object> preprocessArgs(List<?> args) {
        // Make sure argument are interpreted as the correct type
        // (the parser interprets all strings as queries, so we sometimes need to convert them back...)
        List<Object> newArgs = new ArrayList<>(args);
        for (int i = 0; i < args.size(); i++) {
            Object arg = args.get(i);
            if (i >= argTypes.size())
                continue; // either vararg or too many param (will be caught later)
            ArgType type = getExpectedParameterType(i);
            if (Objects.requireNonNull(type) == ArgType.STRING) {
                if (arg instanceof TextPatternRegex) {
                    // Interpret as regular string, not as a query
                    // kind of a hack, but should work
                    String regex = ((TextPatternTerm) arg).getValue();
                    if (regex.startsWith("^") && regex.endsWith("$")) {
                        // strip off ^ and $
                        regex = regex.substring(1, regex.length() - 1);
                    }
                    newArgs.set(i, regex);
                } else if (arg instanceof TextPatternTerm) {
                    // Interpret as regular string, not as a query
                    newArgs.set(i, ((TextPatternTerm) arg).getValue());
                }
            }
        }
        return newArgs;
    }

    public BLSpanQuery apply(QueryExecutionContext context, List<Object> args) {
        // Add any default argument values
        List<Object> newArgs = new ArrayList<>(args);
        int n = Math.max(newArgs.size(), requiredNumberOfArguments());
        for (int i = 0; i < n; i++) {
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
                    newArgs.add(defVal);
                } else if (newArgs.get(i) instanceof SpanQueryDefaultValue) {
                    // Explicitly set to undefined (_); use default value
                    newArgs.set(i, defVal);
                }
            }
            if (newArgs.get(i) == null) {
                // Still null, so no default value available
                throw new InvalidQuery(
                        "Missing argument " + (i + 1) + " for function " + getName() + " (no default value available)");
            }

            // Check argument type
            ArgType expectedType = getExpectedParameterType(i);
            boolean wrongType = switch (expectedType) {
                case QUERY -> !(newArgs.get(i) instanceof BLSpanQuery);
                case STRING -> !(newArgs.get(i) instanceof String);
                default -> true;
            };
            if (wrongType)
                throw new InvalidQuery(
                        "Argument " + (i + 1) + " for function " + getName() + " has the wrong type: expected "
                                + expectedType
                                + ", got " + ArgType.typeOf(newArgs.get(i)));
        }

        if (!isVarArg && newArgs.size() != argTypes.size())
            throw new InvalidQuery(
                    "Wrong number of arguments for query function " + getName() + ": expected " + argTypes.size()
                            + ", got " + newArgs.size());
        return applyFunc(context, newArgs);
    }

    protected abstract BLSpanQuery applyFunc(QueryExecutionContext context, List<Object> parameters);

    public int requiredNumberOfArguments() {
        return isVarArg ? 0 : argTypes.size();
    }

    public Object getDefaultParameterValue(int i) {
        return defaultValues.get(isVarArg ? 0 : i);
    }

    public ArgType getExpectedParameterType(int i) {
        return argTypes.get(isVarArg ? 0 : i);
    }

    public boolean isRelationsFunction() {
        return relationsFunction;
    }

    public String getName() {
        return name;
    }

    public enum ArgType {
        QUERY,
        STRING,
        ELLIPSIS,
        ; // not a real type, used to indicate a variable number of arguments

        public static ArgType typeOf(Object o) {
            if (o instanceof BLSpanQuery)
                return QUERY;
            if (o instanceof String)
                return STRING;
            throw new IllegalArgumentException("Unknown argument type: " + o);
        }
    }
}
