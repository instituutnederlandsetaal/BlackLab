package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.QueryTimings;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.stats.ResultStatsFromOtherResult;

/** A search that yields results.
 * @param <R> results type, e.g. Hits
 */
public abstract class SearchForResults<R extends Results> extends AbstractSearch<R> {

    protected SearchForResults(Search<?> source) {
        super(source);
    }

    SearchForResults(QueryInfo queryInfo, QueryTimings timings) {
        super(queryInfo, timings);
    }

    /**
     * Count hits. All results are fetched in the cache thread.
     *
     * @return resulting operation
     */
    public SearchCount count() {
        return new SearchCountFromResults<>(this, ResultStatsFromOtherResult.CountType.RESULTS);
    }

    /**
     * Count hits. All results are fetched in the cache thread.
     *
     * @return resulting operation
     */
    public SearchCount hitCount() {
        return new SearchCountFromResults<>(this, ResultStatsFromOtherResult.CountType.HITS);
    }

    /**
     * Count docs. All results are fetched in the cache thread.
     *
     * @return resulting operation
     */
    public SearchCount docCount() {
        return new SearchCountFromResults<>(this, ResultStatsFromOtherResult.CountType.DOCS);
    }

}
