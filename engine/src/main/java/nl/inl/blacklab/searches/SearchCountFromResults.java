package nl.inl.blacklab.searches;

import java.util.concurrent.Future;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;
import nl.inl.blacklab.search.results.ResultCount.CountType;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.ResultsStatsDelegate;

/**
 * A search operation that yields a count as its result.
 * @param <T> result type, e.g. Hit
 */
public class SearchCountFromResults<T extends Results> extends SearchCount {

    /**
     * The search we're doing a count for.
     */
    private final SearchForResults<T> source;

    /**
     * Type of count we want (number of hits or docs).
     */
    private final CountType type;

    public SearchCountFromResults(QueryInfo queryInfo, SearchForResults<T> source, CountType type) {
        super(queryInfo);
        this.source = source;
        this.type = type;
    }

    @Override
    public ResultsStats executeInternal(ActiveSearch<ResultsStats> activeSearch) throws InvalidQuery {
        // Start the search and construct the count object
        ResultsStats resultCount = new ResultCount(executeChildSearch(activeSearch, source), type);
        if (activeSearch != null && activeSearch.peek() != null)
            ((ResultsStatsDelegate) activeSearch.peek()).setRealStats(resultCount);

        // Gather all the hits.
        // This runs synchronously, so SearchCountFromResults will not be finished until
        // the entire count it finished. You can peek at the running count in the meantime,
        // however.
        resultCount.waitUntil().allProcessed();

        return resultCount;
    }

    /**
     * Return the peek object, given a cache entry.
     *
     * This object will be returned when SearchCacheEntry.peek() is called while
     * the search is executing. Here we return a ResultsStatsDelegate object that will return 0
     * while there's no real count available yet, but will return the real count once
     * it's available.
     *
     * @param future future result object
     * @return peek object, or null if not supported for this operation
     */
    @Override
    public ResultsStatsDelegate peekObject(Future<ResultsStats> future) {
        // Create a temporary stats object that will return 0 until it receives the
        // real object and will delegate to that.
        return new ResultsStatsDelegate(future);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        @SuppressWarnings("unchecked")
        SearchCountFromResults<T> other = (SearchCountFromResults<T>) obj;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        if (type != other.type)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("countfromresults", source, type);
    }

}
