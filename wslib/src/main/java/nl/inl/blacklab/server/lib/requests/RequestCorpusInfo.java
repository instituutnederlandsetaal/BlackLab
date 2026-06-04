package nl.inl.blacklab.server.lib.requests;

import java.util.Collection;

import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;

/** Request for general information about a corpus
 *
 * @param corpusName corpus to get information about
 * @param listValuesFor annotations to list values for
 * @param limitValues maximum number of values to return
 * @param customInfo include custom info? (not used by BlackLab, e.g. displayName)
 * @param relations what relations information to include
 */
public record RequestCorpusInfo(String corpusName, Collection<String> listValuesFor, long limitValues,
                                boolean customInfo, RequestRelations relations) {

    public static @NonNull RequestCorpusInfo fromParams(QueryParams qpar) {
        RequestRelations relations = RequestRelations.fromParams(qpar); // (null = every field)
        return new RequestCorpusInfo(qpar.getCorpusName(),
                qpar.getList(WsParam.LIST_VALUES_FOR_ANNOTATIONS), qpar.getLong(WsParam.LIMIT_VALUES),
                qpar.getBool(WsParam.INCLUDE_CUSTOM_INFO),
                relations);
    }

}
