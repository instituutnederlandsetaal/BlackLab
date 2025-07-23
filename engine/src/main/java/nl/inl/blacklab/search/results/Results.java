package nl.inl.blacklab.search.results;

public interface Results extends SearchResult {

    /**
     * Get information about the original query.
     * <p>
     * This includes the index, field, max. settings, and max. stats
     * (whether the max. settings were reached).
     *
     * @return query info
     */
    QueryInfo queryInfo();

    ResultsStats resultsStats();

    /**
     * This is an alias of resultsStats().waitUntil().allProcessed().
     *
     * @return number of hits processed total
     */
    long size();

}
