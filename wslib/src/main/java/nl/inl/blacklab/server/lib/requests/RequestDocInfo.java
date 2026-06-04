package nl.inl.blacklab.server.lib.requests;

import java.util.Collection;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;

public record RequestDocInfo(BlackLabIndex index, String docPid, Collection<MetadataField> metadataToInclude) {
    public static RequestDocInfo fromParams(QueryParams qpar) {
        BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
        return new RequestDocInfo(
                index,
                qpar.get(WsParam.DOC_PID),
                ParamUtil.getMetadataToInclude(index, qpar.getList(WsParam.LIST_VALUES_FOR_METADATA_FIELDS))
        );
    }
}
