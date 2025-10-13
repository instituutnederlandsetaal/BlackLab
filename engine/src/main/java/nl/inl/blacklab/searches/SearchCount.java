package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultsStats;

/**
 * A search operation that yields a count as its result.
 */
public abstract class SearchCount extends AbstractSearch<ResultsStats> {

    protected SearchCount(QueryInfo queryInfo) {
        super(queryInfo);
    }

}
