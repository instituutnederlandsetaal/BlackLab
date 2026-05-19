package nl.inl.blacklab.server.lib.requests;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.WebserviceParams;

public record RequestAutocomplete(BlackLabIndex index, String fieldName, String annotationName, String term, String autocompleteType) {
    public static RequestAutocomplete fromParams(QueryParams qpar) {
        String fieldName = qpar.getFieldName();
        String annotationName = qpar.getAnnotationName();

        // Annotated field specified but no annotation?
        BlackLabIndex index = WebserviceParams.index(qpar.getCorpusName());
        if (annotationName == null && index.metadata().annotatedFields().exists(fieldName))
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Also specify a annotation to autocomplete for annotated field: " + fieldName);
        return new RequestAutocomplete(index, fieldName, annotationName, qpar.getTerm(), qpar.getAutocompleteType());
    }
}
