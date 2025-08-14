package nl.inl.blacklab.search.results.hits;

import nl.inl.blacklab.search.results.stats.ResultsStats;

/** Used by ResultsStatsPassive to wait until some number of docs have been seen. */
class ResultsAwaiterDocs implements ResultsStats.ResultsAwaiter {

    HitResultsAbstract results;

    public ResultsAwaiterDocs(HitResultsAbstract hits) {
        this.results = hits;
    }

    @Override
    public boolean processedAtLeast(long lowerBound) {
        // There's no ensureDocsRead() method, so loop until the requested number of docs have been read
        ResultsStats hitsStats = results.resultsStats();
        while (!hitsStats.done() && results.docsStats().processedSoFar() < lowerBound) {
            hitsStats.waitUntil().processedAtLeast(hitsStats.processedSoFar() + 1);
        }
        return results.docsStats().processedSoFar() >= lowerBound;
    }

    @Override
    public long allProcessed() {
        results.resultsStats().waitUntil().allProcessed();
        return results.docsStats().processedSoFar();
    }

    @Override
    public long allCounted() {
        results.resultsStats().waitUntil().allProcessed();
        return results.docsStats().countedSoFar();
    }
}
