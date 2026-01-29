package nl.inl.blacklab.server.lib;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.webservice.WebserviceParameter;

/**
 * Query parameters, parsed from a JSON structure
 */
public class QueryParamsMap extends QueryParamsAbstract {

    /** Our parameters, "re-serialized" from the JSON structure */
    final Map<WebserviceParameter, String> params;

    public QueryParamsMap(String corpusName, SearchManager searchManager, User user, Map<WebserviceParameter, String> params) throws JsonProcessingException {
        super(corpusName, searchManager, user);
        this.params = new EnumMap<>(WebserviceParameter.class);
        this.params.putAll(params);
    }

    @Override
    public Map<WebserviceParameter, String> getParameters() {
        return Collections.unmodifiableMap(params);
    }

    @Override
    protected boolean has(WebserviceParameter par) {
        return params.containsKey(par);
    }

    @Override
    protected String get(WebserviceParameter par) {
        return params.getOrDefault(par, par.getDefaultValue());
    }

}
