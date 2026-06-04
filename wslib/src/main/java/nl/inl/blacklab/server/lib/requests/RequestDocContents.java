package nl.inl.blacklab.server.lib.requests;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.ParamsForResponse;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;

public record RequestDocContents(
        BlackLabIndex index,
        AnnotatedField field,
        RequestHits requestHits,
        String docPid,
        int wordStart,
        int wordEnd,
        ParamsForResponse paramsForResponse) {

    public static RequestDocContents fromParams(QueryParams qpar) {
        BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
        return new RequestDocContents(
                index,
                ParamUtil.getAnnotatedField(index, qpar.get(WsParam.FIELD)),
                RequestHits.optFromParams(qpar, false, null).orElse(null),
                qpar.get(WsParam.DOC_PID),
                qpar.getInt(WsParam.WORD_START),
                qpar.getInt(WsParam.WORD_END),
                qpar
        );
    }
}
