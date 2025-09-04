package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hits.HitsFromFetcher;
import nl.inl.blacklab.search.results.hits.fetch.HitFetcherQuery;
import nl.inl.blacklab.search.results.hits.fetch.HitFilter;
import nl.inl.blacklab.search.results.stats.ResultsStats;

public class HitResultsFromQuery extends HitResultsAbstract {

    /** Global view on our segment hits */
    private final HitsFromFetcher hits;

    protected HitResultsFromQuery(QueryInfo queryInfo, BLSpanQuery sourceQuery,
            SearchSettings searchSettings) {
        super(queryInfo.optOverrideField(sourceQuery));
        sourceQuery.setQueryInfo(queryInfo);
        HitFetcherQuery fetcher = new HitFetcherQuery(sourceQuery, searchSettings);
        hits = new HitsFromFetcher(fetcher, HitFilter.ACCEPT_ALL);
    }

    @Override
    public long numberOfResultObjects() {
        return hits.globalHitsSoFar();
    }

    @Override
    public HitsFromFetcher getHits() {
        return hits;
    }

    @Override
    public ResultsStats resultsStats() {
        return hits.resultsStats();
    }

    @Override
    public ResultsStats docsStats() {
        return hits.docsStats();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public MatchInfoDefs getMatchInfoDefs() {
        return hits.matchInfoDefs();
    }

}
