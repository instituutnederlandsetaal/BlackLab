package nl.inl.blacklab.server.lib;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.Query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.webservice.WsParam;
import nl.inl.util.Json;

/**
 * Query parameters as a typed map.
 */
public class QueryParamsMap implements QueryParams {

    protected final String corpusName;

    private final Map<WsParam, String> params = new EnumMap<>(WsParam.class);

    private final Map<WsParam, Object> typedParams = new EnumMap<>(WsParam.class);

    /** Filter query to use if filter parameter not specified, if any. Used with Solr.  */
    private final Query fallbackFilterQuery;

    /** Config, for determining some default values */
    private final BLSConfig config;

    /** Is this a debug request? If not, we may not see cache info or override the FI match factor. */
    boolean debugMode;

    public QueryParamsMap(String corpusName, Map<WsParam, String> params,
            Map<WsParam, Object> typedParams, Query fallbackFilterQuery,
            BLSConfig config, boolean debugMode) {
        this.corpusName = corpusName;
        this.fallbackFilterQuery = fallbackFilterQuery;
        this.config = config;
        this.debugMode = debugMode;
        if (typedParams == null) {
            this.params.putAll(params);
            for (Map.Entry<WsParam, String> entry: params.entrySet()) {
                this.typedParams.put(entry.getKey(), toAppropriateType(entry.getKey(), entry.getValue()));
            }
        } else {
            this.typedParams.putAll(typedParams);
            for (Map.Entry<WsParam, Object> entry: typedParams.entrySet()) {
                this.params.put(entry.getKey(), toStringRepr(entry.getKey(), entry.getValue()));
            }
        }
    }

    @Override
    public Map<WsParam, String> getParameters() {
        return Collections.unmodifiableMap(params);
    }

    @Override
    public Map<WsParam, Object> getTypedParameters() {
        return Collections.unmodifiableMap(typedParams);
    }

    @Override
    public Query getFallbackFilterQuery() {
        return fallbackFilterQuery;
    }

    /** Get config, for determining some default values */
    @Override
    public BLSConfig config() {
        return config;
    }

    @Override
    public boolean debugMode() {
        return debugMode;
    }

    @Override
    public boolean has(WsParam key) {
        return params.containsKey(key);
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param par parameter type
     * @return value if set
     */
    @Override
    public Optional<String> opt(WsParam par) {
        String value = params.get(par);
        return StringUtils.isEmpty(value) ? Optional.empty() : Optional.of(value);
    }

    @Override
    public String get(WsParam key) {
        return opt(key).orElseGet(key::getDefaultString);
    }

    /**
     * Get parameter value as a boolean.
     *
     * If not explicitly set, uses the configured default value, or false if none configured.
     *
     * @param par parameter type
     * @return value
     */
    @Override
    public boolean getBool(WsParam par) {
        return optBool(par).orElseGet(par::getDefaultBool);
    }

    /**
     * Get parameter value as an integer.
     *
     * If not explicitly set, uses the configured default value, or 0 if none configured.
     *
     * @param par parameter type
     * @return value
     */
    @Override
    public int getInt(WsParam par) {
        return optInteger(par).orElseGet(() -> (int)par.getDefaultLong());
    }

    /**
     * Get parameter value as a long.
     *
     * If not explicitly set, uses the configured default value, or 0 if none configured.
     *
     * @param par parameter type
     * @return value
     */
    @Override
    public long getLong(WsParam par) {
        return optLong(par).orElseGet(par::getDefaultLong);
    }

    /**
     * Get parameter value as a double.
     *
     * If not explicitly set, uses the configured default value, or 0 if none configured.
     *
     * @param par parameter type
     * @return value
     */
    @Override
    public double getDouble(WsParam par) {
        return optDouble(par).orElseGet(par::getDefaultFloat);
    }

    /**
     * Get parameter value as a set of strings.
     *
     * If not explicitly set, uses the configured default value, or an empty set if none configured.
     *
     * @param par parameter type
     * @return value
     */
    @Override
    public Set<String> getSet(WsParam par) {
        return optSet(par).orElse(Set.of());
    }

    /**
     * Get parameter value as a list of strings.
     *
     * If not explicitly set, uses the configured default value, or an empty set if none configured.
     *
     * @param par parameter type
     * @return value
     */
    @Override
    public List<String> getList(WsParam par) {
        return optList(par).orElse(List.of());
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param par parameter type
     * @return value if set
     */
    @Override
    public Optional<Double> optDouble(WsParam par) {
        return opt(par).map(QueryParamsMap::parseDouble);
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param par parameter type
     * @return value if set
     */
    @Override
    public Optional<Integer> optInteger(WsParam par) {
        return opt(par).map(QueryParamsMap::parseInt);
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param par parameter type
     * @return value if set
     */
    @Override
    public Optional<Long> optLong(WsParam par) {
        return opt(par).map(QueryParamsMap::parseLong);
    }

    /**
     * Get parameter value if it was explicitly passed with the request.
     *
     * If not explicitly set, will return an empty Optional.
     *
     * @param par parameter type
     * @return value if set
     */
    @Override
    public Optional<Boolean> optBool(WsParam par) {
        return opt(par).map(QueryParamsMap::parseBoolean);
    }

    /**
     * Get parameter value as a set of strings.
     *
     * If not explicitly set, uses the configured default value, or an empty set if none configured.
     *
     * @param par parameter type
     * @return value
     */
    @Override
    public Optional<Set<String>> optSet(WsParam par) {
        return optList(par).map(LinkedHashSet::new);
    }

    /**
     * Get parameter value as a list of strings.
     *
     * If not explicitly set, uses the configured default value, or an empty set if none configured.
     *
     * @param par parameter type
     * @return value
     */
    @Override
    public Optional<List<String>> optList(WsParam par) {
        String val = get(par).trim();
        if (StringUtils.isEmpty(val))
            return Optional.empty();
        return Optional.of(Arrays.stream(val.split(",")).map(String::trim).toList());
    }

    @Override
    public <T> Optional<T> opt(WsParam par, StringInterpreter<T> interpreter) {
        return opt(par).map(interpreter::interpret);
    }

    @Override
    public String getCorpusName() { return corpusName; }

    /** A way to convert from string to some type */
    @FunctionalInterface
    public interface StringInterpreter<T> {
        T interpret(String value);
    }

    protected static double parseDouble(String value) {
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return 0.0;
    }

    protected static int parseInt(String value) {
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return 0;
    }

    protected static long parseLong(String value) {
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // ok, just return default
            }
        }
        return 0;
    }

    protected static boolean parseBoolean(String value) {
        if (value != null) {
            return switch (value) {
                case "true", "1", "yes", "on" -> true;
                default -> false;
            };
        }
        return false;
    }

    private static String toStringRepr(WsParam par, Object value) {
        return switch (par.type()) {
            case STRING_OR_JSON_OBJECT -> {
                if (value instanceof String str)
                    yield str;
                try {
                    yield Json.getJsonObjectMapper().writeValueAsString(value);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            case FLOAT -> Double.toString((double)value);
            case INTEGER -> Long.toString((long)value);
            case BOOLEAN -> Boolean.toString((boolean)value);
            case JSON -> {
                try {
                    yield Json.getJsonObjectMapper().writeValueAsString(value);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            case STRING -> value.toString();
        };
    }

    public static Object toAppropriateType(WsParam par, String value) {
        return switch (par.type()) {
            case STRING_OR_JSON_OBJECT -> {
                if (value.trim().charAt(0) == '{') {
                    // A JSON value.
                    // (better detection would look at pattlang parameter and/or
                    //  try to parse as BCQL first if pattlang == default)
                    yield toJsonMap(value);
                } else {
                    // Interpret as string (query in some query language, e.g. BCQL)
                    yield value;
                }
            }
            case FLOAT -> parseDouble(value);
            case INTEGER -> parseLong(value);
            case BOOLEAN -> parseBoolean(value);
            case JSON -> toJsonValue(value);
            case STRING -> value;
        };
    }

    private static Object toJsonValue(String value) {
        ObjectMapper jsonMapper = Json.getJsonObjectMapper();
        try {
            return jsonMapper.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Map<String, Object> toJsonMap(String value) {
        ObjectMapper jsonMapper = Json.getJsonObjectMapper();
        try {
            return (Map<String, Object>) jsonMapper.readValue(value, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
