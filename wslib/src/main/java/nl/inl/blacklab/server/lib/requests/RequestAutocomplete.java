package nl.inl.blacklab.server.lib.requests;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;

public record RequestAutocomplete(BlackLabIndex index, String fieldName, String annotationName, String term, String autocompleteType) {
    public static RequestAutocomplete fromParams(QueryParams qpar) {
        String fieldName = qpar.get(WsParam.FIELD);
        String annotationName = qpar.get(WsParam.ANNOTATION);

        // Annotated field specified but no annotation?
        BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
        if (annotationName == null && index.metadata().annotatedFields().exists(fieldName))
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Also specify a annotation to autocomplete for annotated field: " + fieldName);
        return new RequestAutocomplete(index, fieldName, annotationName, qpar.get(WsParam.TERM),
                qpar.get(WsParam.AUTOCOMPLETE_TYPE));
    }
}
