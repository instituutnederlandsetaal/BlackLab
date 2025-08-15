package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.search.results.stats.ResultsStats;

/** Used by ResultsStatsPassive to wait until some number of docs have been seen. */
public class ResultsAwaiterDocs implements ResultsStats.ResultsAwaiter {

    ResultsAwaitable results;

    public ResultsAwaiterDocs(ResultsAwaitable hits) {
        this.results = hits;
    }

    @Override
    public boolean processedAtLeast(long lowerBound) {
        // There's no ensureDocsRead() method, so loop until the requested number of docs have been read
        ResultsStats hitsStats = results.resultsStats();
        while (!hitsStats.done() && results.docsStats().processedSoFar() < lowerBound) {
            hitsStats.processedAtLeast(hitsStats.processedSoFar() + 1);
        }
        return results.docsStats().processedSoFar() >= lowerBound;
    }

    @Override
    public long allProcessed() {
        results.resultsStats().processedTotal();
        return results.docsStats().processedSoFar();
    }

    @Override
    public long allCounted() {
        results.resultsStats().processedTotal();
        return results.docsStats().countedSoFar();
    }
}
