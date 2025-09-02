package nl.inl.blacklab.searches;

import java.util.Objects;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hitresults.HitResults;

/** A search that yields hits. */
public class SearchHitsFromQuery extends SearchHits {

    private final BLSpanQuery spanQuery;

    private final SearchSettings searchSettings;

    public SearchHitsFromQuery(QueryInfo queryInfo, BLSpanQuery spanQuery,
            SearchSettings searchSettings) {
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
    public boolean equals(Object o) {
        if (!(o instanceof SearchHitsFromQuery that))
            return false;
        if (!super.equals(o))
            return false;
        return Objects.equals(spanQuery, that.spanQuery) && Objects.equals(searchSettings,
                that.searchSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), spanQuery, searchSettings);
    }

    @Override
    public String toString() {
        return toString("hits", spanQuery);
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
