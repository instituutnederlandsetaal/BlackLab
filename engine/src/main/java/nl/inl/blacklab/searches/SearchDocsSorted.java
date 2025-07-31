package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.docs.DocResults;

public class SearchDocsSorted extends SearchDocs {

    private final SearchDocs source;

    private final DocProperty property;

    public SearchDocsSorted(QueryInfo queryInfo, SearchDocs docsSearch, DocProperty sortBy) {
        super(queryInfo);
        this.source = docsSearch;
        this.property = sortBy;
    }

    @Override
    public DocResults executeInternal(ActiveSearch<DocResults> activeSearch) throws InvalidQuery {
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
        SearchDocsSorted other = (SearchDocsSorted) obj;
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
