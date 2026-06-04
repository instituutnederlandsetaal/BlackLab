package nl.inl.blacklab.server.lib.requests;

import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;

public record RequestCorpusStatus(Index index, boolean includeCustomInfo) {
    public static RequestCorpusStatus fromParams(QueryParams qpar) {
        String corpusName = qpar.getCorpusName();
        Index index = IndexManager.get().getIndex(corpusName);
        return new RequestCorpusStatus(index, qpar.getBool(WsParam.INCLUDE_CUSTOM_INFO));
    }
}
