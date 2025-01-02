package nl.inl.blacklab.search.extensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryDefaultValue;
import nl.inl.blacklab.search.textpattern.TextPatternRegex;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;

/**
 * Manages extension functions that can be used in queries.
 */
public class QueryExtensions {

    /** Default value for a query parameter that means "any n-gram" ( []* ) */
    public static final String VALUE_QUERY_ANY_NGRAM = "_ANY_NGRAM_";

    /** Prefix for extension functions that enable pseudo-annotations like punctAfter */
    public static final String PSEUDO_ANNOTATION_EXTENSION_FUNCTION_PREFIX = "annot_";

    public static boolean isRelationsFunction(String name) {
        FuncInfo funcInfo = functions.get(name);
        if (funcInfo == null)
            throw new UnsupportedOperationException("Unknown function: " + name);
        return funcInfo.isRelationsFunction();
    }

    enum ArgType {
        QUERY,
        STRING,
        ELLIPSIS, // not a real type, used to indicate a variable number of arguments
    }

    private static class FuncInfo {
        private ExtensionFunction func;

        private List<ArgType> argTypes;

        private List<Object> defaultValues;

        private boolean isVarArg;

        private boolean relationsFunction;

        public FuncInfo(ExtensionFunction func, List<ArgType> argTypes, List<Object> defaultValues) {
            this(func, argTypes, defaultValues, false);
        }

        public FuncInfo(ExtensionFunction func, List<ArgType> argTypes, List<Object> defaultValues, boolean relationsFunction) {
            this.func = func;
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

        private int requiredNumberOfArguments() {
            return isVarArg ? 0 : argTypes.size();
        }

        private Object getDefaultParameterValue(int i) {
            return defaultValues.get(isVarArg ? 0 : i);
        }

        private ArgType getExpectedParameterType(int i) {
            return argTypes.get(isVarArg ? 0 : i);
        }

        public boolean isRelationsFunction() {
            return relationsFunction;
        }
    }

    /** Registry of extension functions by name */
    private static Map<String, FuncInfo> functions = new HashMap<>();

    static {
        register(XFDebug.class);      // Debug functions such as _ident(), _FI1(), _FI2()
        register(XFRelations.class);  // Functions for working with relations
        register(XFPunctBeforeAfter.class);  // Pseudo-annotations punctBefore/punctAfter
    }

    public static void register(Class<? extends ExtensionFunctionClass> extClass) {
        try {
            extClass.getConstructor().newInstance().register();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add a query function to the registry.
     *
     * @param func query extension function
     * @param argTypes argument types
     */
    public static void register(String name, ExtensionFunction func, List<ArgType> argTypes) {
        register(name, argTypes, Collections.emptyList(), func, false);
    }

    /**
     * Add a query function to the registry.
     *
     * @param argTypes      argument types
     * @param defaultValues default values for arguments
     * @param func          query extension function
     */
    public static void register(String name, List<ArgType> argTypes, List<Object> defaultValues, ExtensionFunction func) {
        register(name, argTypes, defaultValues, func, false);
    }

    private static void register(String name, List<ArgType> argTypes, List<Object> defaultValues, ExtensionFunction func,
            boolean relationsFunction) {
        functions.put(name, new FuncInfo(func, argTypes, defaultValues, relationsFunction));
    }

    public static void registerRelationsFunction(String name, List<ArgType> argTypes, List<Object> defaultValues,
            ExtensionFunction func) {
        register(name, argTypes, defaultValues, func, true);
    }

    public static void registerPseudoAnnotation(String name, List<ArgType> argTypes, List<Object> defaultValues,
            ExtensionFunction func) {
        register(pseudoAnnotationFunctionName(name), argTypes, defaultValues, func);
    }

    public static String pseudoAnnotationFunctionName(String annotName) {
        return PSEUDO_ANNOTATION_EXTENSION_FUNCTION_PREFIX + annotName;
    }

    /**
     * Check if a query function exists.
     * @param name name of the query function
     * @return true if it exists, false if not
     */
    public static boolean exists(String name) {
        return functions.containsKey(name);
    }

    /**
     * Make sure we recognize a string arg as a string and not a query.
     *
     * @param name name of the function
     * @param args arguments
     * @return arguments with the correct type
     */
    public static List<?> preprocessArgs(String name, List<?> args) {
        FuncInfo funcInfo = functions.get(name);
        if (funcInfo == null)
            throw new UnsupportedOperationException("Unknown function: " + name);

        // Make sure argument are interpreted as the correct type
        // (the parser interprets all strings as queries, so we sometimes need to convert them back...)
        List<Object> newArgs = new ArrayList<>(args);
        for (int i = 0; i < args.size(); i++) {
            Object arg = args.get(i);
            if (i >= funcInfo.argTypes.size())
                continue; // either vararg or too many param (will be caught later)
            ArgType type = funcInfo.getExpectedParameterType(i);
            switch (type) {
            case STRING:
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
                break;
            }
        }
        return newArgs;
    }

    /**
     * Apply a query extension function.
     * @param name name of the function
     * @param context query execution context
     * @param args arguments
     * @return the query returned by the function
     */
    public static BLSpanQuery apply(String name, QueryExecutionContext context, List<Object> args) {
        FuncInfo funcInfo = functions.get(name);
        if (funcInfo == null)
            throw new UnsupportedOperationException("Unknown function: " + name);

        // Add any default argument values
        List<Object> newArgs = new ArrayList<>(args);
        int n = Math.max(newArgs.size(), funcInfo.requiredNumberOfArguments());
        for (int i = 0; i < n; i++) {
            // Fill in default value for argument if missing
            if (i < funcInfo.defaultValues.size()) {
                Object defVal = funcInfo.getDefaultParameterValue(i);
                if (defVal == QueryExtensions.VALUE_QUERY_ANY_NGRAM) {
                    // Special case: any n-gram (usually meaning "don't care")
                    defVal = SpanQueryAnyToken.anyNGram(context.queryInfo(), context.luceneField());
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
                throw new BlackLabRuntimeException("Missing argument " + (i + 1) + " for function " + name + " (no default value available)");
            }

            // Check argument type
            ArgType expectedType = funcInfo.getExpectedParameterType(i);
            boolean wrongType = true;
            switch (expectedType) {
            case QUERY:
                wrongType = !(newArgs.get(i) instanceof BLSpanQuery);
                break;
            case STRING:
                wrongType = !(newArgs.get(i) instanceof String);
            }
            if (wrongType)
                throw new BlackLabRuntimeException("Argument " + (i + 1) + " for function " + name + " has the wrong type: expected " + expectedType
                        + ", got " + expectedType);
        }

        if (!funcInfo.isVarArg && newArgs.size() != funcInfo.argTypes.size())
            throw new BlackLabRuntimeException("Wrong number of arguments for query function " + name + ": expected " + funcInfo.argTypes.size() + ", got " + newArgs.size());
        return funcInfo.func.apply(context.queryInfo(), context, newArgs);
    }

}
