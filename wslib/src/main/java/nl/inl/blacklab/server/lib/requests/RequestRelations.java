package nl.inl.blacklab.server.lib.requests;

import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;

/** Request for information about relations/tags indexed in an annotated field */
public record RequestRelations(AnnotatedField annotatedField, long limitValues, String relClasses,
                               boolean separateSpans, boolean onlySpans) {

    public static @NonNull RequestRelations fromParams(QueryParams qpar) {
        BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
        return new RequestRelations(
                ParamUtil.getAnnotatedField(index, qpar.get(WsParam.FIELD)),
                qpar.getLong(WsParam.LIMIT_VALUES),
                qpar.get(WsParam.REL_CLASSES),
                qpar.getBool(WsParam.REL_SEPARATE_SPANS),
                qpar.getBool(WsParam.REL_ONLY_SPANS)
        );
    }

    public BlackLabIndex index() {
        return annotatedField.index();
    }

    public RequestRelations withAnnotatedField(AnnotatedField annotatedField) {
        return new RequestRelations(annotatedField, limitValues, relClasses, separateSpans, onlySpans);
    }
}
