package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;

public abstract class HitResultsFromQueryAbstract extends HitResultsAbstract {

    public HitResultsFromQueryAbstract(QueryInfo queryInfo, HitsMutable hits) {
        super(queryInfo, hits, true);
    }

}
