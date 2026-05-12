package nl.inl.blacklab.server.requesthandlers;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import nl.inl.blacklab.server.lib.QueryParamsAbstract;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.ServletUtil;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;
import nl.inl.util.Json;

/** BLS API-specific implementation of WebserviceParams.
 *
 * Extracts the webservice parameters from a HttpServletRequest.
 */
public class QueryParamsBlackLabServer extends QueryParamsAbstract {

    private final Map<WebserviceParameter, String> map = new EnumMap<>(WebserviceParameter.class);

    private final Map<WebserviceParameter, Object> typedMap = new EnumMap<>(WebserviceParameter.class);

    public QueryParamsBlackLabServer(String corpusName, SearchManager searchMan, User user, HttpServletRequest request, WebserviceOperation operation) {
        super(corpusName, searchMan, user);
        for (String name: request.getParameterMap().keySet()) {
            WebserviceParameter par = WebserviceParameter.fromValue(name).orElse(null);
            if (par != null) {
                String value = ServletUtil.getParameter(request, name, "");
                if (value.isEmpty())
                    continue;
                map.put(par, value);
                typedMap.put(par, toAppropriateType(par, value));
            }
        }
        map.put(WebserviceParameter.CORPUS_NAME, corpusName);
        typedMap.put(WebserviceParameter.CORPUS_NAME, corpusName);
        if (operation != null && operation != WebserviceOperation.NONE) {
            map.put(WebserviceParameter.OPERATION, operation.value());
            typedMap.put(WebserviceParameter.OPERATION, operation.value());
        }
    }

    public static Object toAppropriateType(WebserviceParameter par, String value) {
        switch (par.type()) {
            case PATTERN -> {
                if (value.trim().charAt(0) == '{') {
                    // Likely a JSON value.
                    // (better detection would look at pattlang parameter and/or
                    //  try to parse as BCQL first if pattlang == default)
                    return toJsonValue(value);
                } else {
                    // Interpret as string (query in some query language, e.g. BCQL)
                    return value;
                }
            }
            case FLOAT -> {
                return parseDouble(value);
            }
            case INTEGER -> {
                return parseLong(value);
            }
            case BOOLEAN -> {
                return parseBoolean(value);
            }
            case JSON -> {
                return toJsonValue(value);
            }
            default -> {
                return value;
            }
        }
    }

    private static Map<String, Object> toJsonValue(String value) {
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
