package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.WebserviceParams;

public class ResultListOfHits {
    private final WebserviceParams params;
    private final Hits hits;
    private final ConcordanceContext concordanceContext;
    private final Map<Integer, String> docIdToPid;

    ResultListOfHits(WebserviceParams params, Hits hits, ConcordanceContext concordanceContext,
            Map<Integer, String> docIdToPid) {
        this.params = params;
        this.hits = hits;
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

    public Hits getHits() {
        return hits;
    }

    public ConcordanceContext getConcordanceContext() {
        return concordanceContext;
    }

    public Map<Integer, String> getDocIdToPid() {
        return docIdToPid;
    }
}
