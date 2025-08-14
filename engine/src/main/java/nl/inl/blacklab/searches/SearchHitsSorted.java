package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.QueryTimings;
import nl.inl.blacklab.search.results.SearchSettings;

/** A search that yields hits. */
public class SearchHitsSorted extends SearchHits {

    private final SearchHits source;
    private final HitProperty property;

    SearchHitsSorted(QueryInfo queryInfo, SearchHits source, HitProperty sortBy) {
        super(queryInfo);
        this.source = source;
        this.property = sortBy;
    }

    @Override
    public HitResults executeInternal(ActiveSearch<HitResults> activeSearch) throws InvalidQuery {
        QueryTimings timings = queryInfo().timings().start();
        try {
            return executeChildSearch(activeSearch, source).sorted(property);
        } finally {
            timings.record("sort");
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((property == null) ? 0 : property.hashCode());
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
        SearchHitsSorted other = (SearchHitsSorted) obj;
        if (property == null) {
            if (other.property != null)
                return false;
        } else if (!property.equals(other.property))
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
        return toString("sort", source, property);
    }

    @Override
    public SearchSettings searchSettings() {
        return source.searchSettings();
    }
}
