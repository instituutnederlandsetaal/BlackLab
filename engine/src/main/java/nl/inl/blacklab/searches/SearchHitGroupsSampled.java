package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * A search operation that yields groups of hits.
 */
public class SearchHitGroupsSampled extends SearchHitGroups {

    private final SearchHitGroups source;

    private final SampleParameters sampleParameters;

    public SearchHitGroupsSampled(QueryInfo queryInfo, SearchHitGroups source, SampleParameters sampleParameters) {
        super(queryInfo);
        this.source = source;
        this.sampleParameters = sampleParameters;
    }

    @Override
    public HitGroups executeInternal(ActiveSearch<HitGroups> activeSearch) throws InvalidQuery {
        return executeChildSearch(activeSearch, source).sample(sampleParameters);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((sampleParameters == null) ? 0 : sampleParameters.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
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
        SearchHitGroupsSampled other = (SearchHitGroupsSampled) obj;
        if (sampleParameters == null) {
            if (other.sampleParameters != null)
                return false;
        } else if (!sampleParameters.equals(other.sampleParameters))
            return false;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("sample", source, sampleParameters);
    }
}
