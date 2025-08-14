package nl.inl.blacklab.search.results.hits;

import nl.inl.blacklab.search.results.stats.ResultsStats;

class ResultsAwaiterHits implements ResultsStats.ResultsAwaiter {

    private final HitResultsAbstract hits;

    public ResultsAwaiterHits(HitResultsAbstract hits) {
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
