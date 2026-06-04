package nl.inl.blacklab.server.lib.requests;

import java.util.Map;
import java.util.Set;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.webservice.WsParam;

public record RequestTermFrequencies(
        AnnotationSensitivity annotation,
        Query filterQuery,
        Set<String> terms,
        WindowSettings window
) {

    public static RequestTermFrequencies fromParams(QueryParams qpar) {
        BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
        AnnotatedField field = ParamUtil.getAnnotatedField(index, qpar.get(WsParam.FIELD));
        AnnotationSensitivity annotSensitivity = getAnnotationSensitivity(
                field,
                qpar.get(WsParam.ANNOTATION),
                qpar.optBool(WsParam.SENSITIVE).orElse(null));
        return new RequestTermFrequencies(
                annotSensitivity,
                ParamUtil.filterQuery(qpar),
                qpar.getSet(WsParam.TERMS),
                ParamUtil.windowSettings(qpar, false));
    }

    /** Find the AnnotationSensitivity */
    private static AnnotationSensitivity getAnnotationSensitivity(AnnotatedField field, String annotName, Boolean sensitive) {
        if (annotName.isEmpty())
            annotName = field.mainAnnotation().name();
        Annotation annotation = field.annotation(annotName);
        if (annotation == null)
            throw new BadRequest("ANNOTATION_NOT_FOUND",
                    "Annotation '" + annotName + "' not found in field '" + field.name() + "'",
                    Map.of("annotationName", annotName, "fieldName", field.name()));
        boolean defaultToSensitive = !annotation.hasSensitivity(MatchSensitivity.INSENSITIVE);
        if (sensitive == null)
            sensitive = defaultToSensitive;
        MatchSensitivity matchSensitivity = MatchSensitivity.caseAndDiacriticsSensitive(
                sensitive);
        WebserviceOperations.ensureHasSensitivity(annotation, matchSensitivity);
        return annotation.sensitivity(matchSensitivity);
    }

    public BlackLabIndex index() {
        return annotation.annotation().field().index();
    }
}
