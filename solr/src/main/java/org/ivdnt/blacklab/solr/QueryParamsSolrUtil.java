package org.ivdnt.blacklab.solr;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.SolrParams;

import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.lib.QueryParamsMap;
import nl.inl.blacklab.webservice.WsParam;

/**
 * Extracts the webservice parameters from the Solr request parameters.
 * The parameters must be prefixed with "bl." to distinguish them from Solr parameters.
 * (in the future, we may also support a JSON Solr request that doesn't need these prefixes)
 */
public class QueryParamsSolrUtil {

    public static boolean shouldRunComponent(SolrParams params) {
        return params.get(UserRequestSolr.BL_PAR_PREFIX + WsParam.OPERATION) != null || params.get(
                UserRequestSolr.BL_PAR_PREFIX + WsParam.JSON_REQUEST) != null;
    }

    public static QueryParamsMap getParams(String corpusName, SolrParams solrParams, Query fallbackFilterQuery, BLSConfig config,
            boolean debugMode) {
        Map<WsParam, String> params = solrParams.stream()
                .filter(e -> e.getKey().startsWith(UserRequestSolr.BL_PAR_PREFIX)) // Only BL params
                .flatMap(e -> WsParam.fromValue(
                                e.getKey().substring(UserRequestSolr.BL_PAR_PREFIX.length()))
                        .map(par -> Pair.of(par, StringUtils.join(e.getValue(), "; ")))
                        .stream()
                ).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        return new QueryParamsMap(corpusName, params, null, fallbackFilterQuery, config, debugMode);
    }
}
