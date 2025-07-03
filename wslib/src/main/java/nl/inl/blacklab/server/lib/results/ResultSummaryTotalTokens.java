package nl.inl.blacklab.server.lib.results;

public class ResultSummaryTotalTokens {
    private final long totalTokens;

    ResultSummaryTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }
}
