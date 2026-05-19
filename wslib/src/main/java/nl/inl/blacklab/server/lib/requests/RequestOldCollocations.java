package nl.inl.blacklab.server.lib.requests;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.hitresults.ContextSize;

/** Request for (old) collocations (term frequencies in context around hits)
 */
public record RequestOldCollocations(RequestHits requestHits, ContextSize contextSize, boolean sensitive) {
    public static RequestOldCollocations fromHitsRequest(RequestHits reqHits) {
        Annotation annotation = reqHits.searchField().mainAnnotation();
        boolean defaultToSensitive = !annotation.hasSensitivity(MatchSensitivity.INSENSITIVE);
        Boolean sensitive = reqHits.sensitive();
        if (sensitive == null)
            sensitive = defaultToSensitive;
        return new RequestOldCollocations(reqHits, reqHits.contextSettings().size(), sensitive);
    }
}
