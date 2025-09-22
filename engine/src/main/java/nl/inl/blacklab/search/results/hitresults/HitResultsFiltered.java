package nl.inl.blacklab.search.results.hitresults;

import java.util.List;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsFromPublishers;
import nl.inl.blacklab.search.results.hits.fetch.HitFilter;
import nl.inl.blacklab.search.results.hits.fetch.HitFilterPropertyValue;
import nl.inl.blacklab.search.results.hits.fetch.HitPublisher;
import nl.inl.blacklab.search.results.hits.fetch.HitPublisherFilter;
import nl.inl.blacklab.search.results.stats.ResultsStats;

public class HitResultsFiltered extends HitResultsAbstract {

    /** Global view on our segment hits */
    private final HitsFromPublishers hits;

    protected HitResultsFiltered(QueryInfo queryInfo, Hits source,
            HitProperty filterProp, PropertyValue filterValue) {
        super(queryInfo);
        HitFilter filter = new HitFilterPropertyValue(filterProp, filterValue);
        List<HitPublisher> hitPublishers = source.publishersPerSegment();
        if (hitPublishers != null) {
            List<HitPublisherFilter> publishers = hitPublishers.stream()
                    .map(hits -> new HitPublisherFilter(hits, filter))
                    .toList();
            hits = new HitsFromPublishers(publishers, SearchSettings.UNLIMITED);
        } else {
            hits = new HitsFromPublishers(List.of(new HitPublisherFilter(source.publisher(), filter)),
                    SearchSettings.UNLIMITED);
        }
    }

    @Override
    public long numberOfResultObjects() {
        return hits.sizeSoFar();
    }

    @Override
    public Hits getHits() {
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
