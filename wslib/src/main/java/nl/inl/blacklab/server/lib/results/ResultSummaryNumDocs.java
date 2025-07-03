package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.server.lib.SearchTimings;

public class ResultSummaryNumDocs {
    private final boolean isViewDocGroup;
    private final DocResults docResults;
    private final SearchTimings timings;
    private final CorpusSize subcorpusSize;

    ResultSummaryNumDocs(boolean isViewDocGroup, DocResults docResults, SearchTimings timings,
            CorpusSize subcorpusSize) {
        this.isViewDocGroup = isViewDocGroup;
        this.docResults = docResults;
        this.timings = timings;
        this.subcorpusSize = subcorpusSize;
    }

    public boolean isViewDocGroup() {
        return isViewDocGroup;
    }

    public DocResults getDocResults() {
        return docResults;
    }

    public boolean isCountFailed() {
        return timings.getCountTime() < 0;
    }

    public SearchTimings getTimings() {
        return timings;
    }

    public CorpusSize getSubcorpusSize() {
        return subcorpusSize;
    }
}
