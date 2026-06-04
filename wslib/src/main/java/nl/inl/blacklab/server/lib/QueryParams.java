package nl.inl.blacklab.server.lib;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.lucene.search.Query;

import com.fasterxml.jackson.core.JsonProcessingException;

import jakarta.servlet.http.HttpServletRequest;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.util.ServletUtil;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WsParam;

/** BlackLab API endpoint parameters.
 * <p>
 * This only manages the "plain" parameters (i.e. string, int, enum value, etc.),
 * not any complex objects (such as TextPattern or Search instances) derived from them.
 */
public interface QueryParams extends ParamsForResponse {

    /** Get query parameters from a HttpServletRequest */
    static QueryParamsMap fromServletRequest(String corpusName, WebserviceOperation operation,
            HttpServletRequest request, BLSConfig config, boolean debugMode) {
        Map<WsParam, Object> typedParams = new EnumMap<>(WsParam.class);
        for (String name: request.getParameterMap().keySet()) {
            WsParam par = WsParam.fromValue(name).orElse(null);
            if (par != null) {
                String value = ServletUtil.getParameter(request, name, "");
                if (value.isEmpty())
                    continue;
                typedParams.put(par, QueryParamsMap.toAppropriateType(par, value));
            }
        }
        typedParams.put(WsParam.CORPUS_NAME, corpusName);
        if (operation != null && operation != WebserviceOperation.NONE) {
            typedParams.put(WsParam.OPERATION, operation.value());
        }
        return new QueryParamsMap(corpusName, null, typedParams, null, config, debugMode);
    }

    /** Get query parameters from a JSON structure */
    static QueryParamsMap fromJson(String corpusName, WebserviceOperation operation, String json,
            Query fallbackFilterQuery,
            BLSConfig config, boolean debugMode) throws JsonProcessingException {
        return new QueryParamsMap(corpusName, null, ParamUtil.getTypedParams(operation, json), fallbackFilterQuery, config, debugMode);
    }

    /** Config, for determing some parameter defaults */
    BLSConfig config();

    /** Is this a debug request? If not, we may not see cache info or override the FI match factor. */
    boolean debugMode();

    /** Filter query to use if filter parameter not specified, if any. Used with Solr.  */
    default Query getFallbackFilterQuery() { return null; }

    String getCorpusName();

    /**
     * Was a value for this parameter explicitly passed?
     *
     * This disregards any default values configured for the parameter,
     * and only checks if this request included a value for it.
     *
     * @param par parameter type
     * @return true if this request included an explicit value for the parameter
     */
    boolean has(WsParam par);

    /**
     * Get the parameter value.
     *
     * If this request didn't include an explicit value, use the configured default value.
     *
     * @param par parameter type
     * @return value
     */
    String get(WsParam par);

    boolean getBool(WsParam par);

    int getInt(WsParam par);

    long getLong(WsParam par);

    double getDouble(WsParam par);

    Set<String> getSet(WsParam par);

    List<String> getList(WsParam par);

    Optional<String> opt(WsParam par);

    Optional<Double> optDouble(WsParam par);

    Optional<Integer> optInteger(WsParam par);

    Optional<Long> optLong(WsParam par);

    Optional<Boolean> optBool(WsParam par);

    Optional<Set<String>> optSet(WsParam par);

    Optional<List<String>> optList(WsParam par);

    <T> Optional<T> opt(WsParam par, QueryParamsMap.StringInterpreter<T> interpreter);

    /** Override some of the parameters.
     *
     * @param overrides parameters to override
     * @return new QueryParams with the given parameters overridden
     */
    default QueryParams withOverrides(Map<WsParam, Object> overrides) {
        Map<WsParam, Object> typedParams = new LinkedHashMap<>(getTypedParameters());
        typedParams.putAll(overrides);
        return new QueryParamsMap(getCorpusName(), null, typedParams, getFallbackFilterQuery(), config(), debugMode());
    }
}
