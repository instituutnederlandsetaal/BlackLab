package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.ResultsStatsStatic;
import nl.inl.blacklab.server.lib.SearchTimings;

public class ResultSummaryNumHits {
    private ResultsStats hitsStats;
    private ResultsStats docsStats;
    private boolean waitForTotal;
    private SearchTimings timings;
    private CorpusSize subcorpusSize;

    ResultSummaryNumHits(ResultsStats hitsStats, ResultsStats docsStats,
            boolean waitForTotal, SearchTimings timings, CorpusSize subcorpusSize) {
        this.hitsStats = hitsStats;
        this.docsStats = docsStats;
        this.waitForTotal = waitForTotal;
        this.timings = timings;
        this.subcorpusSize = subcorpusSize;
    }

    public ResultsStats getHitsStats() {
        return hitsStats == null ? ResultsStatsStatic.INVALID : hitsStats;
    }

    public ResultsStats getDocsStats() {
        return docsStats;
    }

    public boolean isWaitForTotal() {
        return waitForTotal;
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
