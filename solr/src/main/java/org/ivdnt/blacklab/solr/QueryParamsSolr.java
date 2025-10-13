package org.ivdnt.blacklab.solr;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.solr.common.params.SolrParams;

import nl.inl.blacklab.server.lib.QueryParamsAbstract;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.webservice.WebserviceParameter;

/**
 * Extracts the webservice parameters from the Solr request parameters.
 * The parameters must be prefixed with "bl." to distinguish them from Solr parameters.
 * (in the future, we may also support a JSON Solr request that doesn't need these prefixes)
 */
public class QueryParamsSolr extends QueryParamsAbstract {

    private static final String BL_PAR_NAME = "bl";

    private static final String BL_PAR_NAME_PREFIX = BL_PAR_NAME + ".";

    public static final String PAR_NAME_JSON_REQUEST = "req";

    private final SolrParams solrParams;

    public QueryParamsSolr(String corpusName, SearchManager searchManager, SolrParams params, User user) {
        super(corpusName, searchManager, user);
        solrParams = params;
    }

    public static boolean shouldRunComponent(SolrParams params) {
        return params.get(BL_PAR_NAME_PREFIX + WebserviceParameter.OPERATION) != null || params.get(BL_PAR_NAME_PREFIX + PAR_NAME_JSON_REQUEST) != null;
    }

    @Override
    protected boolean has(WebserviceParameter par) {
        return !StringUtils.isEmpty(solrParams.get(BL_PAR_NAME_PREFIX + par));
    }

    @Override
    protected String get(WebserviceParameter par) {
        return solrParams.get(BL_PAR_NAME_PREFIX + par, par.getDefaultValue());
    }

    @Override
    public Map<WebserviceParameter, String> getParameters() {
        Stream<Pair<WebserviceParameter, String>> params = solrParams.stream()
                .filter(e -> e.getKey().startsWith(BL_PAR_NAME_PREFIX)) // Only BL params
                .map(e -> Pair.of(
                        WebserviceParameter.fromValue( // strip "bl." and convert to WebserviceParameter or null
                                e.getKey().substring(BL_PAR_NAME_PREFIX.length())).orElse(null),
                        StringUtils.join(e.getValue(), "; "))) // join multiple (shouldn't happen)
                .filter(p -> p.getKey() != null); // only existing parameters
        params = Stream.concat(Stream.of(Pair.of(WebserviceParameter.CORPUS_NAME, getCorpusName())), params); // add index name "parameter"
        Map<WebserviceParameter, String> result = params.collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        // ensure "docs" is translated to "docs-grouped" where necessary (for response consistency)
        result.put(WebserviceParameter.OPERATION, getOperation().value());

        return result;
    }

}
