package nl.inl.blacklab.search.results;

class ResultsAwaiterHits implements ResultsStats.ResultsAwaiter {

    private final HitsAbstract results;

    public ResultsAwaiterHits(HitsAbstract results) {
        this.results = results;
    }

    @Override
    public boolean processedAtLeast(long lowerBound) {
        results.ensureResultsRead(lowerBound);
        return results.resultsStats().processedSoFar() >= lowerBound;
    }

    @Override
    public long allProcessed() {
        results.ensureResultsRead(-1);
        return results.resultsStats().processedSoFar();
    }

    @Override
    public long allCounted() {
        results.ensureResultsRead(-1);
        return results.resultsStats().countedSoFar();
    }
}
