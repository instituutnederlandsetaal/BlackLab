package nl.inl.blacklab.server.requesthandlers;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.server.lib.QueryParamsAbstract;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.ServletUtil;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

/** BLS API-specific implementation of WebserviceParams.
 *
 * Extracts the webservice parameters from a HttpServletRequest.
 */
public class QueryParamsBlackLabServer extends QueryParamsAbstract {

    private final Map<WebserviceParameter, String> map = new EnumMap<>(WebserviceParameter.class);

    public QueryParamsBlackLabServer(String corpusName, SearchManager searchMan, User user, HttpServletRequest request, WebserviceOperation operation) {
        super(corpusName, searchMan, user);
        for (String name: request.getParameterMap().keySet()) {
            WebserviceParameter par = WebserviceParameter.fromValue(name).orElse(null);
            if (par != null) {
                String value = ServletUtil.getParameter(request, name, "");
                if (value.isEmpty())
                    continue;
                map.put(par, value);
            }
        }
        map.put(WebserviceParameter.CORPUS_NAME, corpusName);
        if (operation != null && operation != WebserviceOperation.NONE)
            map.put(WebserviceParameter.OPERATION, operation.value());
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
    public String getCorpusName() {
        return get(WebserviceParameter.CORPUS_NAME);
    }

}
