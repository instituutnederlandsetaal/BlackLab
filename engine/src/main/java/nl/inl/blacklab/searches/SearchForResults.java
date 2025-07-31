package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.stats.ResultStatsFromOtherResult;

/** A search that yields results.
 * @param <R> results type, e.g. Hits
 */
public abstract class SearchForResults<R extends Results> extends AbstractSearch<R> {

    SearchForResults(QueryInfo queryInfo) {
        super(queryInfo);
    }

    /**
     * Count hits. All results are fetched in the cache thread.
     *
     * @return resulting operation
     */
    public SearchCount count() {
        return new SearchCountFromResults<>(queryInfo(), this, ResultStatsFromOtherResult.CountType.RESULTS);
    }

    /**
     * Count hits. All results are fetched in the cache thread.
     *
     * @return resulting operation
     */
    public SearchCount hitCount() {
        return new SearchCountFromResults<>(queryInfo(), this, ResultStatsFromOtherResult.CountType.HITS);
    }

    /**
     * Count docs. All results are fetched in the cache thread.
     *
     * @return resulting operation
     */
    public SearchCount docCount() {
        return new SearchCountFromResults<>(queryInfo(), this, ResultStatsFromOtherResult.CountType.DOCS);
    }

}
