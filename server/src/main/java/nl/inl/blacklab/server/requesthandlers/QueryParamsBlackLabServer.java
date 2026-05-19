package nl.inl.blacklab.server.requesthandlers;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.lib.QueryParamsAbstract;
import nl.inl.blacklab.server.lib.QueryParamsMap;
import nl.inl.blacklab.server.util.ServletUtil;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

/** BLS API-specific implementation of QueryParams.
 *
 * Extracts the webservice parameters from a HttpServletRequest.
 */
public class QueryParamsBlackLabServer extends QueryParamsAbstract {

    private final Map<WebserviceParameter, String> map = new EnumMap<>(WebserviceParameter.class);

    private final Map<WebserviceParameter, Object> typedMap = new EnumMap<>(WebserviceParameter.class);


    public QueryParamsBlackLabServer(String corpusName, WebserviceOperation operation, HttpServletRequest request, BLSConfig config, boolean debugMode) {
        super(corpusName, config, debugMode);
        for (String name: request.getParameterMap().keySet()) {
            WebserviceParameter par = WebserviceParameter.fromValue(name).orElse(null);
            if (par != null) {
                String value = ServletUtil.getParameter(request, name, "");
                if (value.isEmpty())
                    continue;
                map.put(par, value);
                typedMap.put(par, QueryParamsMap.toAppropriateType(par, value));
            }
        }
        map.put(WebserviceParameter.CORPUS_NAME, corpusName);
        typedMap.put(WebserviceParameter.CORPUS_NAME, corpusName);
        if (operation != null && operation != WebserviceOperation.NONE) {
            map.put(WebserviceParameter.OPERATION, operation.value());
            typedMap.put(WebserviceParameter.OPERATION, operation.value());
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
