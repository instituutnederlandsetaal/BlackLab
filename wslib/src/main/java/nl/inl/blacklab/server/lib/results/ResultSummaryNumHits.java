package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsSaved;
import nl.inl.blacklab.server.lib.SearchTimings;

public record ResultSummaryNumHits(ResultsStats hitsStats, ResultsStats docsStats, boolean waitForTotal,
                                   SearchTimings timings, CorpusSize subcorpusSize) {

    @Override
    public ResultsStats hitsStats() {
        return hitsStats == null ? ResultsStatsSaved.INVALID : hitsStats;
    }

    public boolean isCountFailed() {
        return timings.getCountTime() < 0;
    }
}
