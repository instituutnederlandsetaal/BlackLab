package nl.inl.blacklab.server.lib.requests;

import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.WebserviceParams;

/** Request for information about relations/tags indexed in an annotated field */
public record RequestRelations(AnnotatedField annotatedField, long limitValues, String relClasses,
                               boolean separateSpans, boolean onlySpans) {

    public static @NonNull RequestRelations fromParams(QueryParams qpar) {
        BlackLabIndex index = WebserviceParams.index(qpar.getCorpusName());
        return new RequestRelations(
                WebserviceParams.getAnnotatedField(index, qpar.getFieldName()),
                qpar.getLimitValues(),
                qpar.getRelClasses(),
                qpar.getRelSeparateSpans(),
                qpar.getRelOnlySpans()
        );
    }

    public BlackLabIndex index() {
        return annotatedField.index();
    }

    public RequestRelations withAnnotatedField(AnnotatedField annotatedField) {
        return new RequestRelations(annotatedField, limitValues, relClasses, separateSpans, onlySpans);
    }
}
