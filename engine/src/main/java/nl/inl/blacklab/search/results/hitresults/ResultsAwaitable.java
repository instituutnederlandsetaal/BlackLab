package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.search.results.stats.ResultsStats;

public interface ResultsAwaitable {

    boolean ensureResultsRead(long lowerBound);

    ResultsStats resultsStats();

    ResultsStats docsStats();
}
