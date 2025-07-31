package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.WebserviceParams;

public class ResultListOfHits {
    private final WebserviceParams params;
    private final HitResults hitResults;
    private final ConcordanceContext concordanceContext;
    private final Map<Integer, String> docIdToPid;

    ResultListOfHits(WebserviceParams params, HitResults hitResults, ConcordanceContext concordanceContext,
            Map<Integer, String> docIdToPid) {
        this.params = params;
        this.hitResults = hitResults;
        this.concordanceContext = concordanceContext;
        this.docIdToPid = docIdToPid;
    }

    public Collection<Annotation> getAnnotationsToWrite() {
        Collection<Annotation> annotationsToList = null;
        if (!concordanceContext.isConcordances())
            annotationsToList = WebserviceOperations.getAnnotationsToWrite(params);
        return annotationsToList;
    }

    public WebserviceParams getParams() {
        return params;
    }

    public HitResults getHits() {
        return hitResults;
    }

    public ConcordanceContext getConcordanceContext() {
        return concordanceContext;
    }

    public Map<Integer, String> getDocIdToPid() {
        return docIdToPid;
    }
}
