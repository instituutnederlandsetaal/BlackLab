package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;

/** A search that yields hits. */
public class SearchHitsFiltered extends SearchHits {

    private final SearchHits source;
    private final HitProperty property;
    private final PropertyValue value;

    SearchHitsFiltered(QueryInfo queryInfo, SearchHits source, HitProperty property, PropertyValue value) {
        super(queryInfo);
        this.source = source;
        this.property = property;
        this.value = value;
    }

    @Override
    public HitResults executeInternal(ActiveSearch<HitResults> activeSearch) throws InvalidQuery {
        return executeChildSearch(activeSearch, source).filter(property, value);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((property == null) ? 0 : property.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
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
        SearchHitsFiltered other = (SearchHitsFiltered) obj;
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
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("filter", source, property, value);
    }

    @Override
    public boolean isAnyTokenQuery() {
        return source.isAnyTokenQuery();
    }

    @Override
    public SearchSettings searchSettings() {
        return source.searchSettings();
    }
}
