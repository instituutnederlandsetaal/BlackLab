package nl.inl.blacklab.server.lib;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.webservice.WebserviceParameter;
import nl.inl.util.Json;

/**
 * Query parameters, parsed from a JSON structure
 */
public class QueryParamsMap extends QueryParamsAbstract {

    private final Map<WebserviceParameter, String> map = new EnumMap<>(WebserviceParameter.class);

    private final Map<WebserviceParameter, Object> typedMap = new EnumMap<>(WebserviceParameter.class);

    public QueryParamsMap(String corpusName, Map<WebserviceParameter, String> params,
            Map<WebserviceParameter, Object> typedParams, BLSConfig config, boolean debugMode) {
        super(corpusName, config, debugMode);
        if (typedParams == null) {
            this.map.putAll(params);
            for (Map.Entry<WebserviceParameter, String> entry: params.entrySet()) {
                this.typedMap.put(entry.getKey(), toAppropriateType(entry.getKey(), entry.getValue()));
            }
        } else {
            this.typedMap.putAll(typedParams);
            for (Map.Entry<WebserviceParameter, Object> entry: typedParams.entrySet()) {
                this.map.put(entry.getKey(), toStringRepr(entry.getKey(), entry.getValue()));
            }
        }
    }

    public static String toStringRepr(WebserviceParameter par, Object value) {
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

    public static Object toAppropriateType(WebserviceParameter par, String value) {
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

    @Override
    protected boolean has(WebserviceParameter key) {
        return map.containsKey(key);
    }

    @Override
    protected String get(WebserviceParameter key) {
        String value = map.get(key);
        if (StringUtils.isEmpty(value)) {
            value = key.getDefaultValue();
        }
        return value;
    }

    /**
     * Get a view of the parameters.
     *
     * @return the view
     */
    @Override
    public Map<WebserviceParameter, String> getParameters() {
        return Collections.unmodifiableMap(map);
    }

    @Override
    public Map<WebserviceParameter, Object> getTypedParameters() {
        return Collections.unmodifiableMap(typedMap);
    }

    @Override
    public String getCorpusName() {
        return get(WebserviceParameter.CORPUS_NAME);
    }
}
