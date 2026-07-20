package nl.inl.blacklab.search.extensions;

import java.util.Collections;
import java.util.List;

import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PluginParam;

/**
 * Manages extension functions that can be used in queries.
 */
public class QueryExtensions {

    private QueryExtensions() {
    }

    static {
        register(XFDebug.class);      // Debug functions such as _ident(), _FI1(), _FI2()
        register(XFRelations.class);  // Functions for working with relations
        register(XFPunctBeforeAfter.class);  // Pseudo-annotations punctBefore/punctAfter
        register(XFSpans.class);      // Functions for working with spans
    }

    public static void register(Class<? extends ExtensionFunctionClass> extClass) {
        try {
            extClass.getConstructor().newInstance().register();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Add a query function to the registry.
     *
     * @param func query extension function
     * @param argTypes argument types
     */
    public static void register(String name, ExtensionFunction func, List<PluginParam> argTypes) {
        register(name, argTypes, Collections.emptyList(), func, false);
    }

    /**
     * Add a query function to the registry.
     *
     * @param argTypes      argument types
     * @param defaultValues default values for arguments
     * @param func          query extension function
     */
    public static void register(String name, List<PluginParam> argTypes, List<Object> defaultValues, ExtensionFunction func) {
        register(name, argTypes, defaultValues, func, false);
    }

    private static void register(String name, List<PluginParam> argTypes, List<Object> defaultValues, ExtensionFunction func,
            boolean relationsFunction) {
        register(new QueryFunctionLambda(name, func, argTypes, defaultValues, relationsFunction));
    }

    public static void register(QueryFunction func) {
        PluginManager.type(QueryFunction.class).add(func.getName(), func);
    }

    public static void registerRelationsFunction(String name, List<PluginParam> argTypes, List<Object> defaultValues,
            ExtensionFunction func) {
        register(name, argTypes, defaultValues, func, true);
    }

    /**
     * Get query function
     * @param name function name
     * @return query function
     */
    public static QueryFunction get(String name) {
        QueryFunction queryFunction = getInternal(name);
        if (queryFunction == null)
            throw new UnsupportedOperationException("Unknown function: " + name);
        return queryFunction;
    }

    /**
     * Check if a query function exists.
     * @param name name of the query function
     * @return true if it exists, false if not
     */
    public static boolean exists(String name) {
        return getInternal(name) != null;
    }

    private static QueryFunction getInternal(String name) {
        return PluginManager.type(QueryFunction.class).getIfExists(name).orElse(null);
    }

}
