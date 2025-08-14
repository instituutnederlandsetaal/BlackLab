package nl.inl.blacklab.searches;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;

/** A search that yields hits. */
public class SearchHitsFromQuery extends SearchHits {

    private final BLSpanQuery spanQuery;

    private final SearchSettings searchSettings;

    public SearchHitsFromQuery(QueryInfo queryInfo, BLSpanQuery spanQuery, SearchSettings searchSettings) {
        super(queryInfo);
        if (spanQuery == null)
            throw new IllegalArgumentException("Must specify a query");
        this.spanQuery = spanQuery;
        this.searchSettings = searchSettings;
    }

    /**
     * Execute the search operation, returning the final response.
     *
     * @return result of the operation
     */
    @Override
    public HitResults executeInternal(ActiveSearch<HitResults> activeSearch) {
        return queryInfo().index().find(queryInfo(), spanQuery, searchSettings);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((spanQuery == null) ? 0 : spanQuery.hashCode());
        result = prime * result + ((searchSettings == null) ? 0 : searchSettings.hashCode());
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
        SearchHitsFromQuery other = (SearchHitsFromQuery) obj;
        if (spanQuery == null) {
            if (other.spanQuery != null)
                return false;
        } else if (!spanQuery.equals(other.spanQuery))
            return false;
        if (searchSettings == null) {
            if (other.searchSettings != null)
                return false;
        } else if (!searchSettings.equals(other.searchSettings))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("hits", spanQuery);
    }

    public BLSpanQuery query() {
        return spanQuery;
    }

    @Override
    public boolean isAnyTokenQuery() {
        return spanQuery instanceof SpanQueryAnyToken &&
                spanQuery.guarantees().producesSingleTokens();
    }

    @Override
    public Query getFilterQuery() {
        return spanQuery;
    }

    @Override
    public SearchSettings searchSettings() {
        return searchSettings;
    }
}
