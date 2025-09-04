package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsFromFetcher;
import nl.inl.blacklab.search.results.hits.fetch.HitFetcher;
import nl.inl.blacklab.search.results.hits.fetch.HitFetcherFilterHits;
import nl.inl.blacklab.search.results.hits.fetch.HitFilter;
import nl.inl.blacklab.search.results.hits.fetch.HitFilterPropertyValue;
import nl.inl.blacklab.search.results.stats.ResultsStats;

public class HitResultsFiltered extends HitResultsAbstract {

    /** Global view on our segment hits */
    private final HitsFromFetcher hits;

    protected HitResultsFiltered(QueryInfo queryInfo, Hits toFilter,
            HitProperty filterProp, PropertyValue filterValue) {
        super(queryInfo);
        HitFetcher fetcher = new HitFetcherFilterHits(toFilter);
        HitFilter filter = new HitFilterPropertyValue(filterProp, filterValue);
        hits = new HitsFromFetcher(queryInfo.timings(), fetcher, filter);
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
