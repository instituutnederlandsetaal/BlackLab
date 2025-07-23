package nl.inl.blacklab.search.results;

/**
 * A list of results of some type.
 *
 * All subclasses should be thread-safe.
 *
 * @param <T> result type, e.g. Hit
 */
public abstract class ResultsAbstract implements Results {

    /** Information about the original query: index, field, max settings, max stats. */
    private final QueryInfo queryInfo;

    protected ResultsAbstract(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
    }

    /**
     * Get information about the original query.
     *
     * This includes the index, field, max. settings, and max. stats
     * (whether the max. settings were reached).
     *
     * @return query info
     */
    @Override
    public QueryInfo queryInfo() {
        return queryInfo;
    }

    public abstract String toString();

    /**
     * Ensure that we have read at least as many results as specified in the parameter.
     *
     * @param number the minimum number of results that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     */
    protected abstract void ensureResultsRead(long number);

    @Override
    public long size() {
        return resultsStats().waitUntil().allProcessed();
    }

}
