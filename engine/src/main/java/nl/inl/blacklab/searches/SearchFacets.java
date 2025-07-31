package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.stats.Facets;

/** A search that yields multiple doc groupings with only counts (no stored results). */
public class SearchFacets extends AbstractSearch<Facets> {

    private final SearchDocs source;
    private final List<DocProperty> properties;

    public SearchFacets(QueryInfo queryInfo, SearchDocs source, List<DocProperty> properties) {
        super(queryInfo);
        this.source = source;
        this.properties = properties;
    }

    @Override
    public Facets executeInternal(ActiveSearch<Facets> activeSearch) throws InvalidQuery {
        return new Facets(executeChildSearch(activeSearch, source), properties);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((properties == null) ? 0 : properties.hashCode());
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
        SearchFacets other = (SearchFacets) obj;
        if (properties == null) {
            if (other.properties != null)
                return false;
        } else if (!properties.equals(other.properties))
            return false;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        return true;
    }
}
