package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.search.results.stats.ResultsStats;

/** An object that supports getting results stats updates and waiting for results to be available. */
public interface ResultsAwaitable {

    /**
     * Ensure that we have read at least as many results as specified in the parameter.
     *
     * @param lowerBound the minimum number of results that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     * @return true if the requested number of results were read, false if there are fewer results
     */
    boolean ensureResultsRead(long lowerBound);

    ResultsStats resultsStats();

    ResultsStats docsStats();
}
