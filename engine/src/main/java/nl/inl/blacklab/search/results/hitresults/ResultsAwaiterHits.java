package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.search.results.stats.ResultsStats;

public class ResultsAwaiterHits implements ResultsStats.ResultsAwaiter {

    private final ResultsAwaitable hits;

    public ResultsAwaiterHits(ResultsAwaitable hits) {
        this.hits = hits;
    }

    @Override
    public boolean processedAtLeast(long lowerBound) {
        return hits.ensureResultsRead(lowerBound);
    }

    @Override
    public long allProcessed() {
        hits.ensureResultsRead(-1);
        return hits.resultsStats().processedSoFar();
    }

    @Override
    public long allCounted() {
        hits.ensureResultsRead(-1);
        return hits.resultsStats().countedSoFar();
    }
}
