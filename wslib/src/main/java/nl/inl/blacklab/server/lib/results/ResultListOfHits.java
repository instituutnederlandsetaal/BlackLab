package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.lib.ConcordanceContext;

public class ResultListOfHits {
    private final HitResults hitResults;
    private final ConcordanceContext concordanceContext;
    private final Map<Integer, String> docIdToPid;
    private final ContextSettings contextSettings;
    private final Collection<Annotation> annotationsToList;
    private final boolean omitEmptyCaptures;

    ResultListOfHits(HitResults hitResults, ConcordanceContext concordanceContext, Map<Integer, String> docIdToPid,
            ContextSettings contextSettings, Collection<Annotation> annotationsToList,
            boolean omitEmptyCaptures) {
        this.hitResults = hitResults;
        this.concordanceContext = concordanceContext;
        this.docIdToPid = docIdToPid;
        this.contextSettings = contextSettings;
        this.annotationsToList = annotationsToList;
        this.omitEmptyCaptures = omitEmptyCaptures;
    }

    public Collection<Annotation> getAnnotationsToList() {
        return annotationsToList;
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

    public ContextSettings getContextSettings() {
        return contextSettings;
    }

    public boolean getOmitEmptyCaptures() {
        return omitEmptyCaptures;
    }
}
