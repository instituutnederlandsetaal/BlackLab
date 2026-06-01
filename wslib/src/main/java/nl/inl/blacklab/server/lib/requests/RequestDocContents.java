package nl.inl.blacklab.server.lib.requests;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.server.lib.ParamsForResponse;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.WebserviceParams;

public record RequestDocContents(
        BlackLabIndex index,
        AnnotatedField field,
        RequestHits requestHits,
        String docPid,
        int wordStart,
        int wordEnd,
        ParamsForResponse paramsForResponse) {

    public static RequestDocContents fromParams(QueryParams qpar) {
        BlackLabIndex index = WebserviceParams.index(qpar.getCorpusName());
        return new RequestDocContents(
                index,
                WebserviceParams.getAnnotatedField(index, qpar.getFieldName()),
                RequestHits.optFromParams(qpar, false, null).orElse(null),
                qpar.getDocPid(),
                qpar.getWordStart(),
                qpar.getWordEnd(),
                qpar
        );
    }
}
