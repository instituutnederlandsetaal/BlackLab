package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.ResultsStatsStatic;
import nl.inl.blacklab.server.lib.SearchTimings;

public class ResultSummaryTotalTokens {
    private long totalTokens;

    ResultSummaryTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }
}
