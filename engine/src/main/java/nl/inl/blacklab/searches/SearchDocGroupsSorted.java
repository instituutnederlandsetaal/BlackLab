package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.docs.DocGroups;

/** A search that yields groups of documents. */
public class SearchDocGroupsSorted extends SearchDocGroups {

    private final SearchDocGroups source;

    private final DocGroupProperty property;

    public SearchDocGroupsSorted(QueryInfo queryInfo, SearchDocGroups source, DocGroupProperty property) {
        super(queryInfo);
        this.source = source;
        this.property = property;
    }

    @Override
    public DocGroups executeInternal(ActiveSearch<DocGroups> activeSearch) throws InvalidQuery {
        return executeChildSearch(activeSearch, source).sort(property);
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
        SearchDocGroupsSorted other = (SearchDocGroupsSorted) obj;
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
}
