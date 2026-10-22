package nl.inl.blacklab.searches;

import java.util.Objects;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryTimings;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPatternAnyToken;

/** A search that yields hits. */
public class SearchHitsFromQuery extends SearchHits {

    private final CompleteQuery completeQuery;

    private final SearchSettings searchSettings;

    public SearchHitsFromQuery(Context context, CompleteQuery completeQuery, SearchSettings searchSettings) {
        super(context);
        if (completeQuery == null)
            throw new IllegalArgumentException("Must specify a query");
        this.completeQuery = completeQuery;
        this.searchSettings = searchSettings;
    }

    /**
     * Execute the search operation, returning the final response.
     *
     * @return result of the operation
     */
    @Override
    public HitResults executeInternal(ActiveSearch<HitResults> activeSearch) {
        QueryTimings timings = timings().start();
        try {
            return indexAndField().index().find(indexAndField(), getCombinedSpanFilterQuery(), searchSettings);
        } finally {
            timings.record("fetch");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SearchHitsFromQuery that))
            return false;
        if (!super.equals(o))
            return false;
        return Objects.equals(completeQuery, that.completeQuery) && Objects.equals(searchSettings,
                that.searchSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), completeQuery, searchSettings);
    }

    @Override
    public String toString() {
        return toString("hits", completeQuery);
    }

    @Override
    public boolean isSingleAnyTokenQuery() {
        return completeQuery.pattern() instanceof TextPatternAnyToken any && any.getMin() == 1 && any.getMax() == 1;
    }

    @Override
    public BLSpanQuery getCombinedSpanFilterQuery() {
        return completeQuery.pattern().toQuery(indexAndField(), completeQuery.filter());
    }

    @Override
    public Query getFilterQuery() {
        return completeQuery.filter();
    }

    public CompleteQuery getCompleteQuery() {
        return completeQuery;
    }

    @Override
    public SearchSettings searchSettings() {
        return searchSettings;
    }
}
