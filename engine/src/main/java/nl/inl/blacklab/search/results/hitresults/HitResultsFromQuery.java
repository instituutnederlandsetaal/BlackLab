package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hits.FetchFromQuery;
import nl.inl.blacklab.search.results.hits.HitsFromQuery;
import nl.inl.blacklab.search.results.stats.ResultsStats;

public class HitResultsFromQuery extends HitResultsAbstract {

    /** Global view on our segment hits */
    private final HitsFromQuery hits;

    protected HitResultsFromQuery(QueryInfo queryInfo, BLSpanQuery sourceQuery,
            SearchSettings searchSettings) {
        super(queryInfo.optOverrideField(sourceQuery));
        sourceQuery.setQueryInfo(queryInfo);
        FetchFromQuery fetcher = new FetchFromQuery(sourceQuery, searchSettings, queryInfo().field());
        hits = new HitsFromQuery(queryInfo.timings(), fetcher);
    }

    @Override
    public long numberOfResultObjects() {
        return hits.globalHitsSoFar();
    }

    @Override
    public HitsFromQuery getHits() {
        return hits;
    }

    @Override
    public boolean ensureResultsRead(long number) {
        return hits.ensureResultsRead(number);
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
